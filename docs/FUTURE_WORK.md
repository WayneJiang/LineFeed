# LineFeed 未來設計（未實作）— 若有更多時間：「N 則新文章」pill 與依定位顯示天氣

> **狀態：未實作。** 這是「若有更多時間」會做的兩個功能的設計筆記，不是排定的工作；目前程式碼沒有任何對應變更，PLAN.md §1.3 的兩個延後項目維持延後。
> 目的：說明這兩個功能在現有架構下「會怎麼做、難點在哪、怎麼測」，證明延後是取捨而不是做不到。
> 這份文件只有設計，沒有實作；程式碼庫中沒有任何部分反映這裡的內容。文中的類別、方法、DB 版本等都是「若實作時」的提案。
> 規劃：Opus（分析與設計）。前提文件：`docs/PLAN.md`（§1.3、§3、§3.3）、`docs/NOTES.md`、`DECISIONS.md`；未提到的部分沿用 PLAN.md。

## 預估工作量

- **F1「N 則新文章」pill**：約 3–4 小時（domain 計數規則與測試 0.5 小時、repository 偷看 + 測試 1 小時、Feed 畫面 / ViewModel / pill UI 1–1.5 小時、模擬器驗證 0.5–1 小時）。風險低：不改 schema、不改 mediator。
  - 為什麼這次沒做：屬於 nice-to-have 的 UX 改善；時間優先給 must-have（新鮮度、離線、收藏）與其測試，現行「只在 Feed 可見時刷新並捲回頂端」已是正確、可預期的行為。
- **F2 依定位顯示天氣**：約 7–9 小時（domain 型別與規則 1 小時、LocationManager / Geocoder 與 Robolectric 測試 2 小時、Room v2 migration 與 MigrationTestHelper 1–1.5 小時、repository / refresher 整合 1.5 小時、權限流程 UI 1.5–2 小時、模擬器驗證 1 小時）。風險中：Robolectric 定位 shadow、migration 測試環境、模擬器 coarse 定位（見 §5）。
  - 為什麼這次沒做：對評分重點（新鮮度、離線）幫助不大，卻需要 DB schema 升級與 migration，以及權限流程（拒絕 / 永久拒絕 / 定位關閉）在裝置上逐一手動驗證，這些是單元測試涵蓋不到的，時間成本最高。
- 合計約 1.5 個工作天；若只能挑一個，先做 F1（成本低、直接改善閱讀體驗）。

---

## 0. 目標與非目標

- **目標**
  - F1：回前景的自動文章刷新**不再把使用者捲回頂端、也不再丟掉閱讀位置**；使用者不在頂端時改顯示「N 則新文章」pill，點擊 → 捲到頂端並載入新內容；手動捲到頂端也會觸發同樣行為並收起 pill。
  - F2：天氣改依裝置**大略位置**（`ACCESS_COARSE_LOCATION`）；完整處理「尚未詢問 / 允許 / 拒絕 / 永久拒絕 / 定位服務關閉 / 取不到位置或逾時」，取不到時退回台北並**清楚標示**。
  - 所有新判斷邏輯都是純函式或只依賴 interface，能用 JVM 單元測試；Android API 部分用 Robolectric `@Config(sdk = [35])`。
- **非目標**
  - 不做文章「重疊合併」（PLAN §1.3 延後項目維持延後）；REFRESH 仍是 transaction 內清空重建。
  - 不做背景定位、不要求 `ACCESS_FINE_LOCATION`、不加 Google Play Services、不加 Accompanist。
  - 不做多地點天氣、不做地點搜尋、不做 zh-TW 在地化（新字串照現有慣例寫在 `values/strings.xml` 英文；中文文案寫在本文件方便之後補 `values-zh-rTW`）。
  - 不做 Compose UI 測試（沿用 PLAN §1.3 的取捨）；UI 行為靠純函式 + ViewModel 測試 + 模擬器手動驗證。

---

## 1. F1：「N 則新文章」pill

### 1.1 為什麼不能「刷新後保留位置」

- `ArticleRemoteMediator.refresh()` 在 transaction 內 `clearAll()` 後只寫回第一頁 20 筆（`sortIndex` 0..19）。使用者若正在讀第 45 筆，刷新後 **DB 裡根本沒有那一筆**；Room `PagingSource.getRefreshKey` 以 anchorPosition 換算 offset，新的 PagingSource 只剩 20 筆，LazyColumn 只能跳回清單尾端或頂端。現行程式碼的 `listState.scrollToItem(0)` 其實是在掩蓋這件事。
- 就算只讀第一頁，新文章插在最上面後所有 `sortIndex` 平移，`TopStory`（`sortIndex == 0`）與服務卡位置（`ServiceCardSlots` 以 sortIndex 計算）都會變，第一個可見 item 的 key 可能已不存在。
- 結論：**使用者不在頂端時，不要執行 REFRESH**；只「偷看」伺服器第一頁、計算 N，等使用者主動回到頂端（點 pill 或手動捲上去）才真的 REFRESH。這就是 Twitter/LINE TODAY 類 app 的標準做法，也跟現有「清空重建」語意完全相容。

### 1.2 行為規則

- **自動刷新（FOREGROUND / NETWORK_RESTORED，`pendingArticleRefreshId` 出現時）**
  - 使用者在頂端（`listState.firstVisibleItemIndex == 0`，也就是 weather hero 仍可見）或清單是空的 / 全螢幕狀態 → 直接 `lazyPagingItems.refresh()`（原地刷新），**移除** `scrollToItem(0)`。
  - 使用者不在頂端 → 不 refresh，改呼叫 `viewModel.onArticleRefreshDeferred(id)` → ViewModel 呼叫 `articleRepository.checkForNewArticles()` → N > 0 時 `uiState.newArticles` 出現 → 畫面顯示 pill。
- **點 pill**：`listState.scrollToItem(0)` → `lazyPagingItems.refresh()` → `viewModel.onNewArticlesConsumed()`。順序是先捲再刷：捲到 index 0 後第一個可見 item 是 key `"weather"`（永遠存在、永遠在 index 0 的非分頁 item），Paging 替換資料時 LazyColumn 以這個 key 當錨點，新文章自然出現在 hero 下方，不會跳。
- **手動捲到頂端**：`snapshotFlow { listState.firstVisibleItemIndex == 0 }` 搭配 `uiState.newArticles != null` → 行為同點 pill（不用再捲）。這也涵蓋「check 還在跑時使用者已捲回頂端，結果晚到」的競態：一到就會被立刻消化。
- **下拉重新整理**：使用者必然在頂端 → 行為不變（`refresh()`），不顯示 pill；若 pill 仍存在一併清掉。
- **在 Saved 分頁時**：沿用現有行為，請求保留到回 Feed 才處理（屆時依當下捲動位置走上面兩條路之一）。
- 用 `scrollToItem`（非 `animateScrollToItem`）：從第 100 筆動畫捲回來很慢且會觸發沿途圖片載入。

### 1.3 N 的計算（放 domain，純函式）

- 新檔 `core/domain/.../refresh/NewArticleCounter.kt`：
  ```kotlin
  data class ArticleStamp(val id: Long, val publishedAt: Instant)

  /** Articles on the server's first page that are strictly "above" everything cached. */
  fun countNewArticles(
      cachedNewestPublishedAt: Instant?,   // MAX(publishedAtMillis) of the cache
      knownIds: Set<Long>,                 // which of the fetched ids already exist in the cache
      fetched: List<ArticleStamp>,
  ): Int
  ```
- 規則：`cachedNewestPublishedAt == null` → 0（呼叫端另外回 `NoCache`）；否則計數 `id !in knownIds && publishedAt >= cachedNewestPublishedAt`。
  - 用「id 不在快取」而不是只比時間：同一篇文章被編輯（`updated_at` 變、id 相同）不算新文章。
  - 另外要求 `publishedAt >= 快取最新時間`：API 回補一篇較舊的文章（id 沒看過但時間在快取最新之下）不在「上方」，不算。
  - `>=` 而非 `>`：同一秒發布、id 不同的文章要算。
- `NewArticlesCheck`（domain，`refresh/NewArticlesCheck.kt`）：
  ```kotlin
  sealed interface NewArticlesCheck {
      data class Found(val count: Int, val isCapped: Boolean) : NewArticlesCheck // isCapped: count == page size → UI 顯示 "20+"
      data object UpToDate : NewArticlesCheck
      data object NoCache : NewArticlesCheck
      data class Failed(val error: AppError) : NewArticlesCheck
      data object Offline : NewArticlesCheck
  }
  ```

### 1.4 Data 層：`ArticleRepository.checkForNewArticles()`

- `ArticleRepository` 新增 `suspend fun checkForNewArticles(): NewArticlesCheck`。
- `OfflineFirstArticleRepository` 建構子加入 `ArticleRemoteDataSource`、`NetworkMonitor`、`AppClock`（皆已有 Hilt binding）。流程：
  1. 離線 → `Offline`（不打網路）。
  2. `feedArticleDao.maxPublishedAtMillis()`（新 DAO query）為 null → `NoCache`。
  3. `remoteDataSource.fetchPage(limit = PAGE_SIZE, publishedAtLte = null)`——**與 mediator REFRESH 完全相同的 URL**：點 pill 後的 REFRESH 在 OkHttp cache `max-age=600` 內會直接命中快取，等於「偷看」的那 5 KB 被重用，不會多花流量，而且畫面內容與 N 一致。
  4. `dao.existingIds(fetchedIds)` → `countNewArticles(...)`。
  5. N == 0 → `syncMetadataDao.markSuccess(ARTICLES, now)` 並回 `UpToDate`（已向伺服器確認快取就是最新，TTL 重新計時，避免每次回前景都再偷看）。N > 0 → **不**動 sync_metadata（快取確實過期），回 `Found`。
  6. 例外 → `Failed(error.toAppError())`，只 `markFailure`，快取不動。
- **不寫 `feed_articles`**：「`ArticleRemoteMediator` 是唯一寫入文章快取的程式碼」這條不變式維持成立。
- 新 DAO：`@Query("SELECT MAX(publishedAtMillis) FROM feed_articles") suspend fun maxPublishedAtMillis(): Long?`（`publishedAtMillis` 已有 index）。

### 1.5 Feature 層

- `FeedUiState` 新增 `val newArticles: NewArticlesBadge? = null`；`data class NewArticlesBadge(val count: Int, val isCapped: Boolean)`。
- `FeedViewModel`：
  - `fun onArticleRefreshDeferred(id: Long)`：`handledArticleRefreshId = id`，`viewModelScope.launch { when (val r = articleRepository.checkForNewArticles()) { is Found -> newArticles.value = NewArticlesBadge(r.count, r.isCapped); else -> Unit } }`。失敗靜默（使用者沒要求，不跳 snackbar）。
  - `fun onNewArticlesConsumed()`：清掉 `newArticles`。
  - `onArticleRefreshHandled(id)` 保留（在頂端原地刷新時用）。
  - `onPullToRefresh()` 一併清掉 `newArticles`。
  - `combine` 已有 4 個輸入（coreSignals, lastUpdated, handledId, userMessage），加 `newArticles` 變 5 個，未超過 PLAN §11 的上限。
- `FeedScreen`：
  - `LaunchedEffect(pendingArticleRefreshId)`：`if (isFeedAtTop(listState.firstVisibleItemIndex) || lazyPagingItems.itemCount == 0) { refresh(); onArticleRefreshHandled(id) } else onArticleRefreshDeferred(id)`。
  - `isFeedAtTop(firstVisibleItemIndex: Int): Boolean = firstVisibleItemIndex == 0` 放 `FeedScreenState.kt`（純函式；日後改門檻只改一處）。
  - `LaunchedEffect(listState, uiState.newArticles)` 內 `snapshotFlow { isFeedAtTop(listState.firstVisibleItemIndex) }.first { it }` → `refresh()` + `onNewArticlesConsumed()`。
  - 新 composable `cells/NewArticlesPill.kt`：`FeedContent` 外包一層 `Box`，pill 以 `Alignment.TopCenter` 疊在 LazyColumn 上，`AnimatedVisibility(slideInVertically + fadeIn)`；M3 `Surface(shape = CircleShape, color = primary)` + 向上箭頭 icon + 文字；有 contentDescription。
  - 字串：`<plurals name="feed_new_articles_pill">`（one: `%d new article` / other: `%d new articles`）與 `feed_new_articles_pill_capped`（`%d+ new articles`）。中文文案：「N 則新文章」「20+ 則新文章」。

### 1.6 F1 邊界情況

- check 進行中又來一次 FOREGROUND：以最新結果覆蓋（N 永遠是「相對於快取」算的，不需要累加）。
- 快取被使用者往下捲 APPEND 了很多頁：不影響 N（只看快取最新時間與 id）。
- 第一頁 20 筆全是新的 → `isCapped`，顯示「20+」。
- check 失敗 / 離線：不顯示 pill、不跳訊息；下一次觸發再試。
- pill 顯示中使用者點文章進 Detail 再返回：`uiState` 在 ViewModel、`listState` 在 back stack entry 的 saveable，兩者都保留。
- 設定變更（旋轉）：pill 保留（在 ViewModel）；process death 後 pill 消失，可接受（下次回前景會重算）。
- 在頂端原地刷新時看不到「有新東西」的提示：刻意如此（使用者本來就在看最新內容，新文章直接出現在 hero 下方）。

---

## 2. F2：依定位顯示天氣

### 2.1 關鍵決策

- **只要 `ACCESS_COARSE_LOCATION`**
  - 天氣資料本身是網格（Open-Meteo 約 1–11 km），大略位置（約 2–3 km）完全足夠；精確位置沒有任何好處。
  - 權限對話框較不嚇人、允許率較高；Android 12+ 使用者本來就可以把精確降成大略，只要求 coarse 讓兩種情況行為一致。
  - 隱私：送給 Open-Meteo 與存進 Room 的座標再四捨五入到小數 2 位（約 1 km），不傳比需要更精確的資料。
  - 代價：coarse 權限下拿不到 `GPS_PROVIDER`，只能用 `FUSED_PROVIDER`（API 31+）/ `NETWORK_PROVIDER`；在沒有網路定位供應者的裝置上會取不到位置 → 走台北 fallback（已被流程涵蓋）。
- **不加 Play Services，用 `LocationManager` + `androidx.core` 的 `LocationManagerCompat`**
  - `LocationManagerCompat.getCurrentLocation(...)` 在 API 30 以下自動 backport（minSdk 24 可用）；`LocationManagerCompat.isLocationEnabled(...)` 判斷定位服務開關。`core-ktx` 已經是 `core:data` 依賴，零新增套件。
  - Fused Location Provider 的優勢（省電的融合定位、舊裝置也有 fused）對「每次回前景最多一次的大略定位」幾乎沒有差別；而它會引入 GMS 依賴、在無 GMS 裝置 / AOSP 模擬器上不可用、還要處理 `Task` 轉 coroutine。取捨：接受在少數無網路定位供應者的裝置上退回台北。
- **權限請求放在 Compose feature 層**：`rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`，永久拒絕判斷用 `LocalActivity.current`（activity-compose 1.13 已提供）的 `shouldShowRequestPermissionRationale`。不需要 Accompanist。
- **冷啟動絕不主動跳權限對話框**：預設顯示台北並在 hero 卡上放一個行內按鈕「使用目前位置」，由使用者決定。按鈕文字本身就是第一次的說明；只有在「曾被拒絕、系統建議顯示說明」時才先跳一個 rationale dialog。
- **地名**：Open-Meteo forecast 沒有反向地理編碼，geocoding API 也只做正向搜尋。採 `android.location.Geocoder`（`Geocoder.isPresent()` 為 false、逾時 3 秒、或丟 `IOException` 時回 null），取 `locality ?: subAdminArea ?: adminArea`；取不到名字時 UI 顯示「目前位置」。地名在**每次天氣刷新時**解析並存進 Room（離線也能顯示）；新座標與快取座標相距 < 5 km 時沿用快取地名，不重打 Geocoder。
- **快取 key 與「位置變了就視為過期」**：`weather_snapshot` 仍只存一列（只顯示一個地點），另存座標與 fallback 原因；「位置是否大幅改變」以 haversine 距離 > **5 km** 判斷（比「座標取整當 key」好：不會在格線邊界來回翻轉）。這個判斷獨立於 TTL：TTL 內位置變了也要重抓。

### 2.2 Domain（`core:domain`，純 JVM）

- `location/Coordinates.kt`
  ```kotlin
  data class Coordinates(val latitude: Double, val longitude: Double) {
      fun distanceKmTo(other: Coordinates): Double   // haversine, R = 6371.0
      fun rounded(decimals: Int = 2): Coordinates
      companion object { val TAIPEI = Coordinates(25.0330, 121.5654) }
  }
  ```
- `location/LocationProvider.kt`
  ```kotlin
  enum class LocationFallbackReason { PERMISSION_NOT_GRANTED, LOCATION_DISABLED, UNAVAILABLE }

  sealed interface LocationResult {
      data class Available(val coordinates: Coordinates) : LocationResult
      data class Unavailable(val reason: LocationFallbackReason) : LocationResult
  }

  interface LocationProvider {
      /** Cheap, never starts an active fix (permission check + services check + last known). */
      suspend fun lastKnown(): LocationResult
      /** May request a fresh coarse fix, bounded by a timeout; falls back to last known. */
      suspend fun current(): LocationResult
  }
  ```
- `model/Weather.kt`：`locationName: String` 改為 `place: WeatherPlace`
  ```kotlin
  data class WeatherPlace(
      val name: String?,                               // null → UI 顯示「目前位置」
      val coordinates: Coordinates,
      val fallbackReason: LocationFallbackReason?,     // 非 null → 這是台北 fallback
  ) { val isFallback: Boolean get() = fallbackReason != null }
  ```
- `location/WeatherLocationRules.kt`（純函式，F2 的核心邏輯都在這）
  ```kotlin
  const val SIGNIFICANT_MOVE_KM = 5.0
  fun targetPlaceFor(result: LocationResult): WeatherPlace   // Unavailable → TAIPEI + reason, name = "Taipei"
  fun requiresLocationRefetch(cached: WeatherPlace?, probe: LocationResult, thresholdKm: Double = SIGNIFICANT_MOVE_KM): Boolean
  ```
  `requiresLocationRefetch` 規則：
  - `cached == null` → true。
  - probe `Available`：快取是 fallback → true；否則 `distance > thresholdKm`。
  - probe `Unavailable(r)`、快取是裝置位置：`r == PERMISSION_NOT_GRANTED` → true（權限被撤銷，改顯示台北並誠實標示）；其他原因 → false（定位暫時關閉或取不到，沿用上次的當地天氣）。
  - probe `Unavailable(r)`、快取是 fallback：`r != cached.fallbackReason` → true（例如剛授權但還沒有 last known：reason 從 PERMISSION_NOT_GRANTED 變 UNAVAILABLE，值得嘗試一次主動定位；重抓台北只 0.4 KB 也能更新標示文字）。
- `freshness/RefreshTrigger.kt`：新增 `LOCATION_CHANGED`。`FreshnessPolicy` 不需改（沒有特別規則，照 TTL 評估）；天氣會因為下面的「情境過期」被抓。`refreshTriggers()` 不產生這個 trigger，只有 ViewModel 在使用者剛授權時送出。

### 2.3 Data（`core:data`）

- `location/AndroidLocationProvider`（`internal`，`@Inject constructor(@ApplicationContext context, clock: AppClock)`，`@Singleton`）：
  - 權限：`ContextCompat.checkSelfPermission(context, ACCESS_COARSE_LOCATION)`，未允許 → `Unavailable(PERMISSION_NOT_GRANTED)`。
  - 服務：`LocationManagerCompat.isLocationEnabled(lm)` false → `Unavailable(LOCATION_DISABLED)`。
  - 供應者：`lm.getProviders(true)`（系統已依權限過濾），優先 `FUSED_PROVIDER`（API 31+）→ `NETWORK_PROVIDER` → 其餘；沒有任何可用 → `UNAVAILABLE`。
  - `lastKnown()`：各供應者 `getLastKnownLocation` 取最新一筆，年齡 ≤ 30 分鐘才算數。
  - `current()`：先 `lastKnown()`；沒有 → `suspendCancellableCoroutine` 包 `LocationManagerCompat.getCurrentLocation(lm, provider, CancellationSignal, executor, consumer)`，外層 `withTimeoutOrNull(10.seconds)`，取消時 `signal.cancel()`；仍沒有 → 接受 ≤ 24 小時的舊 last known；再沒有 → `UNAVAILABLE`。
  - `SecurityException`（檢查後權限被撤銷的競態）→ `PERMISSION_NOT_GRANTED`。
  - timeout / 年齡門檻以建構子預設參數注入，方便測試。
- `location/PlaceNameResolver`（`internal interface { suspend fun nameFor(c: Coordinates): String? }`）+ `GeocoderPlaceNameResolver`：API 33+ 用 listener 版 `getFromLocation(lat, lon, 1, listener)`，以下用同步版包在 `withContext(ioDispatcher)`；`withTimeoutOrNull(3.seconds)`；任何例外 → null。
- `OpenMeteoApi.getForecast(latitude, longitude, ...)`：拿掉 Taipei 預設值與固定 `timezone = "Asia/Taipei"`，改 `timezone = "auto"`（讓 `daily` 日期與 `current.time` 以當地時區計算）。`TAIPEI_*` 常數移到 `Coordinates.TAIPEI`。
- `WeatherRemoteDataSource.fetchForecast(coordinates: Coordinates)`。
- `WeatherSnapshotEntity`（DB v2）：
  - `locationKey` 固定為 `CURRENT_LOCATION_KEY = "current"`（仍是單列表；取代 `TAIPEI_LOCATION_KEY`）。
  - `locationName: String?`、新增 `latitude: Double`、`longitude: Double`、`fallbackReason: String?`（`LocationFallbackReason.name`，null = 裝置位置）。
- `WeatherMappers`：`toEntity(fetchedAt, place: WeatherPlace)`；`toDomain` 組出 `WeatherPlace`（未知的 `fallbackReason` 字串 → 視為 `UNAVAILABLE`，防禦性）。
- `OfflineFirstWeatherRepository`（加入 `LocationProvider`、`PlaceNameResolver`）：
  - `refresh()`：`current()` → `targetPlaceFor` → 若非 fallback：座標 `rounded()`，快取存在且距離 < 5 km 且有名字則沿用名字，否則 `placeNameResolver.nameFor()` → `fetchForecast(place.coordinates)` → upsert。地名解析與天氣抓取用 `coroutineScope { async }` 平行，地名失敗不影響天氣。
  - 新增 `override suspend fun isContextStale(): Boolean = requiresLocationRefetch(weatherDao.get()?.toDomain(...)?.place, locationProvider.lastKnown())`——只用便宜的 `lastKnown()`，不會讓整個 refresh 卡 10 秒。
- `SourceRefresher` 新增 `suspend fun isContextStale(): Boolean = false`（預設實作；服務卡不用改）。
- `DefaultFeedRefresher`：`toFetch = refreshers.filter { shouldFetch(...) || (network != OFFLINE && it.isContextStale()) }`（`||` 短路：TTL 已決定要抓就不再查位置）。`LOCATION_CHANGED` **不**在 `articleTriggersThatBumpTheRequestCounter` 內，不會引發文章刷新 / pill。
- DI：`DataModule` 加 `@Binds LocationProvider`、`@Binds PlaceNameResolver`。
- 權限宣告：`app/src/main/AndroidManifest.xml` 加 `<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />`（放 app：一眼看得到整個 App 要了哪些權限）。

### 2.4 Room v1 → v2 migration

- `LineFeedDatabase` `version = 2`，`Room.databaseBuilder(...).addMigrations(MIGRATION_1_2)`；`database/Migrations.kt`：
  - `DROP TABLE weather_snapshot` → `CREATE TABLE weather_snapshot (...)`（SQL **直接複製** Room 產生的 `schemas/.../2.json` 的 `createSql`，避免手寫與 Room 驗證不一致）→ `DELETE FROM sync_metadata WHERE source = 'WEATHER'`（讓下次觸發必定重抓）。
  - 為什麼重建而不是 `ALTER TABLE ADD COLUMN`：天氣是可拋棄的快取（0.4 KB，下一次觸發就回來），而 `ADD COLUMN ... DEFAULT` 必須在 entity 上寫對應的 `@ColumnInfo(defaultValue)` 否則 Room schema 驗證失敗——多一個容易出錯的地方換不到任何價值。
  - 為什麼**不**用 `fallbackToDestructiveMigration()`：會連 `bookmarks`（使用者資料）一起清掉，與 LineFeedDatabase 的 KDoc 原則衝突。只重建天氣表 = 「只對天氣快取破壞性」、其他表原封不動，而且是明確、可測的 migration。
  - 不加 `fallbackToDestructiveMigrationOnDowngrade`：NOTES 記錄的 4→1 降版 crash 是模擬器上殘留他案 App 的操作問題，不該用會清掉收藏的設定掩蓋。
- 提交 `core/data/schemas/.../2.json`。
- 測試：`testImplementation(libs.room.testing)`（catalog 新增 `room-testing`，同 `room` 版本）；`android { sourceSets["test"].assets.srcDir("$projectDir/schemas") }` 讓 Robolectric 的 `MigrationTestHelper` 讀得到 schema。

### 2.5 Feature（`feature:feed`）

- 權限狀態（UI 層專屬，不進 ViewModel——它依賴 Activity）：
  ```kotlin
  enum class LocationPermissionStatus { GRANTED, NOT_GRANTED, SHOW_RATIONALE, PERMANENTLY_DENIED }
  ```
  - `location/rememberLocationPermissionController()`：持有 `status`、`request()`；`LifecycleResumeEffect` 每次 ON_RESUME 重新 `checkSelfPermission`（從系統設定回來時更新）。
  - launcher 結果 false：`shouldShowRequestPermissionRationale == true` → `SHOW_RATIONALE`；false → `PERMANENTLY_DENIED`（存 `rememberSaveable`）。
  - 已知限制：跨 process 不記「曾被永久拒絕」；下次 session 第一次點按鈕系統會直接回拒絕、不顯示對話框，此時依上一條立刻切到 `PERMANENTLY_DENIED` 並跳 snackbar「定位權限已關閉，可到設定開啟」附「設定」動作——不為此引入 DataStore / SharedPreferences（PLAN §2.4）。
- 狀態機（使用者可見）：
  - 尚未詢問（NOT_GRANTED）→ 卡片標示「Taipei · 預設位置」+ 按鈕「使用目前位置」→ 點擊直接開系統對話框。
  - 允許 → `viewModel.onLocationPermissionGranted()` → `feedRefresher.refresh(LOCATION_CHANGED)` → 天氣 in-flight 時卡片顯示小型「定位中…」進度 → 顯示當地天氣與地名（或「目前位置」）。
  - 拒絕但可再問（SHOW_RATIONALE）→ 按鈕仍在；點擊先顯示 rationale `AlertDialog`（「只使用大略位置來顯示你所在地區的天氣；不會在背景定位，也不會上傳精確座標」）→ 確認後再開系統對話框。
  - 永久拒絕 → 按鈕改「到設定開啟」→ `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$pkg")`。從設定回來：ON_RESUME 更新按鈕；process 的 ON_START 觸發 FOREGROUND，`isContextStale()` 發現快取是 fallback 而 probe 可用 → 自動重抓。
  - 已允許但定位服務關閉（快取 `fallbackReason == LOCATION_DISABLED`）→ 標示「定位服務已關閉，顯示台北」+ 按鈕「開啟定位」→ `Settings.ACTION_LOCATION_SOURCE_SETTINGS`。
  - 已允許但取不到 / 逾時（`UNAVAILABLE`）→ 標示「無法取得目前位置，顯示台北」，不放按鈕（下拉重新整理即重試）。
  - 已允許且快取仍是 `PERMISSION_NOT_GRANTED` 的 fallback（剛授權、重抓中）→ 不放按鈕，顯示定位中。
- 純函式（JVM 可測）`WeatherLocationUi.kt`：
  ```kotlin
  enum class WeatherLocationAction { NONE, REQUEST_PERMISSION, OPEN_APP_SETTINGS, OPEN_LOCATION_SETTINGS }
  data class WeatherLocationLabel(val nameRes: Int?, val name: String?, val noteRes: Int?)
  fun weatherLocationAction(place: WeatherPlace?, permission: LocationPermissionStatus): WeatherLocationAction
  fun weatherLocationLabel(place: WeatherPlace): WeatherLocationLabel
  ```
  規則：permission 非 GRANTED → 依 status 回 `REQUEST_PERMISSION` / `OPEN_APP_SETTINGS`（`SHOW_RATIONALE` 也是 `REQUEST_PERMISSION`，差別在 UI 先跳 dialog）；GRANTED → 只有 `fallbackReason == LOCATION_DISABLED` 回 `OPEN_LOCATION_SETTINGS`，其餘 `NONE`。
- `WeatherCardState.Available` 新增 `isLocating: Boolean`（= `WEATHER in refreshStatus.inFlight` 且 `!userInitiated`；下拉時已有 pull 指示器）。
- `WeatherHeroCard(state, now, locationAction, onLocationAction)`：地名列右側顯示 location icon（裝置位置）或「預設」標籤（fallback）；有 action 時在卡片底部放 `TextButton`（白字）。Preview 補 fallback / 裝置位置 / 定位服務關閉三種。
- `FeedViewModel.onLocationPermissionGranted()`：`viewModelScope.launch { feedRefresher.refresh(RefreshTrigger.LOCATION_CHANGED) }`。
- 新字串（英文為 base，中文為對照）：`feed_weather_use_my_location`「使用目前位置」、`feed_weather_default_location`「預設位置」、`feed_weather_current_location`「目前位置」、`feed_weather_locating`「定位中…」、`feed_weather_location_disabled`「定位服務已關閉，顯示台北」、`feed_weather_location_unavailable`「無法取得目前位置，顯示台北」、`feed_weather_open_settings`「到設定開啟」、`feed_weather_enable_location`「開啟定位」、`feed_location_rationale_title` / `_body` / `_confirm`、`feed_location_permission_denied_snackbar`。

### 2.6 F2 邊界情況

- 授權後離線：`LOCATION_CHANGED` 被 policy 以 OFFLINE 跳過；恢復連線的 `NETWORK_RESTORED` 會因 `isContextStale()` 抓到。
- 使用者在系統設定撤銷權限：Android 會殺掉 process → 冷啟動 → `isContextStale()` 回 true → 顯示台北 + 「使用目前位置」。
- Android 12+ 使用者只給「大略」：本來就只要 coarse，行為相同。「只允許這一次」：下次 session 權限消失，同上一條。
- 移動中（通勤）：每次觸發距離 > 5 km 才重抓，每次 0.4 KB，可接受。
- 位置在海上 / 國外：Open-Meteo 全球可用；`timezone=auto` 讓日期正確；Geocoder 回 null → 「目前位置」。
- `current()` 10 秒逾時期間：只阻塞天氣這個 async，不影響服務卡；畫面維持快取 + 定位中。
- Geocoder 在無 GMS 裝置 `isPresent()` false → 永遠顯示「目前位置」，不影響功能。
- DB 升級：v1 使用者升級後天氣表為空 → hero 卡 Loading → 下一次觸發（冷啟動）抓回；收藏不受影響。

---

## 3. 測試計畫

- **`core:domain`（JVM，自帶 `FixedClock`，不依賴 `core:testing`——NOTES Step 3 的依賴環）**
  - `NewArticleCounterTest`：快取最新時間 null → 0；全部已知 → 0；上方 3 篇新 → 3；相同 id（被編輯）不算；id 沒看過但時間較舊的回補文章不算；同一秒不同 id 算；空的 fetched → 0。
  - `CoordinatesTest`：同一點距離 0；台北 101 ↔ 台北車站約 4–5 km（容許誤差）；台北 ↔ 高雄約 290–300 km；`rounded(2)` 四捨五入。
  - `WeatherLocationRulesTest`：`targetPlaceFor` 三種原因都回 TAIPEI + 對應 reason、Available 回裝置座標；`requiresLocationRefetch` 逐條覆蓋 §2.2 的規則（含 4.9 km false / 5.1 km true 邊界）。
  - `FreshnessPolicyTest` 補一條：`LOCATION_CHANGED` 照 TTL 評估（fresh → Skip）。
- **`core:testing`**
  - `FakeLocationProvider(var lastKnownResult, var currentResult)`，記錄 `currentCalls` 次數。
  - `FakeArticleRepository` 加 `var newArticlesCheck: NewArticlesCheck = UpToDate` 與呼叫次數；`TestData` 的 `Weather` 樣本改用 `WeatherPlace`。
- **`core:data`（Robolectric `@Config(sdk = [35])` 視需要）**
  - `OfflineFirstArticleRepositoryTest` 新增：離線 → `Offline` 且沒打網路；空快取 → `NoCache` 且沒打網路；有新文章 → `Found(n)`，`feed_articles` 內容與 `lastSuccessAt` 皆未改變；無新文章 → `UpToDate` 且 `lastSuccessAt` 更新；20 筆全新 → `isCapped`；網路錯誤 → `Failed` 且 `lastError` 寫入、快取不動。
  - `FeedArticleDaoTest`：`maxPublishedAtMillis` 空表 null / 有資料回最大值。
  - `AndroidLocationProviderTest`（Robolectric，`ShadowLocationManager`、`shadowOf(app).grantPermissions`）：無權限 → `PERMISSION_NOT_GRANTED`；定位關閉 → `LOCATION_DISABLED`；30 分鐘內 last known → Available 且不主動定位；last known 過舊 + `simulateLocation` → Available；沒有任何 fix → 逾時後 `UNAVAILABLE`（注入短 timeout）。
  - `GeocoderPlaceNameResolverTest`（Robolectric `ShadowGeocoder`）：有 locality → 回 locality；只有 adminArea → 回 adminArea；空結果 → null。若 shadow 在 API 33 listener 版行為不穩，改只測「例外 / 空結果回 null」並在 NOTES 記錄。
  - `OfflineFirstWeatherRepositoryTest` 更新：Available → 以 rounded 座標呼叫 remote、存地名與座標；Unavailable → 以台北座標抓並存 reason；Geocoder 回 null → name null 但天氣照存；新位置 < 5 km 沿用快取地名且不呼叫 resolver；≥ 5 km 重新解析；`isContextStale` 以 `FakeLocationProvider.lastKnownResult` 驗證且**不**呼叫 `current()`。
  - `DefaultFeedRefresherTest` 新增：TTL 內但 `isContextStale()` 為 true → 仍抓天氣、服務卡照 TTL 跳過；離線時不因 context stale 而抓；`LOCATION_CHANGED` 不遞增 `articleRefreshRequestId`；TTL 已決定要抓時不呼叫 `isContextStale()`。
  - `WeatherMappersTest` 更新：`WeatherPlace` 往返、未知 reason 字串 → `UNAVAILABLE`。
  - `OpenMeteoApiTest`：request 帶 `latitude`/`longitude`/`timezone=auto`。
  - `LineFeedDatabaseMigrationTest`（Robolectric + `MigrationTestHelper`）：v1 插入一筆 bookmark、一筆 weather、WEATHER 與 ARTICLES 的 sync_metadata → `runMigrationsAndValidate(2, MIGRATION_1_2)` → bookmark 完整保留、weather 表為空且欄位正確、WEATHER sync_metadata 被刪、ARTICLES 保留。
- **`feature:feed`（JVM）**
  - `FeedViewModelTest` 新增：`onArticleRefreshDeferred` → `Found(3)` 時 `newArticles == NewArticlesBadge(3, false)` 且 `pendingArticleRefreshId` 清空；`UpToDate` / `Failed` / `Offline` → 無 pill、無 userMessage；`onNewArticlesConsumed` 清掉；第二次 deferred 以新結果覆蓋；`onPullToRefresh` 清掉 pill；`onLocationPermissionGranted` 送出 `LOCATION_CHANGED`；天氣 in-flight 且非使用者觸發 → `Available.isLocating == true`。
  - `FeedScreenStateTest` 補：`isFeedAtTop(0) == true`、`isFeedAtTop(1) == false`。
  - `WeatherLocationUiTest`：`weatherLocationAction` 對 4 種權限狀態 × 4 種 place（裝置 / 三種 fallback reason）的關鍵組合；`weatherLocationLabel` 對 fallback 與 name null 的輸出。
- **模擬器手動驗證（非實體手機；NOTES Step 13 的教訓）**
  - F1：捲到第 30 筆左右 → Home → 等 TTL（或用 `adb shell cmd` 調時間 / 臨時縮短 TtlConfig 的 debug build）→ 回 App：位置不變、出現 pill；點 pill 回頂端並看到新文章；另測手動捲回頂端。
  - F2：全新安裝冷啟動不跳對話框；「使用目前位置」→ 允許 → `adb emu geo fix 139.69 35.68`（東京）後下拉 → 地名 / 天氣更新；拒絕兩次 → 按鈕變「到設定開啟」；關閉定位服務 → 對應標示。

---

## 4. 建議的 commit 拆法（若實作）

若日後實作，建議依下列順序切成小 commit；每個 commit 都應 `./gradlew unitTest`（或至少受影響模組）全綠再提交，遇到的坑照慣例記在 `docs/NOTES.md`。F1（Step 1–3）與 F2（Step 4–8）彼此獨立，可以只做其中一組。

- **Step 1** — `feat(domain): add new-article counting rule and check result type`
  - `ArticleStamp`、`countNewArticles`、`NewArticlesCheck`、`NewArticleCounterTest`。純新增，不改介面。
- **Step 2** — `feat(data): check for new articles without replacing the cached feed`
  - `ArticleRepository.checkForNewArticles()`、`OfflineFirstArticleRepository` 實作、`FeedArticleDao.maxPublishedAtMillis`、`FakeArticleRepository` 更新、repository / DAO 測試。
- **Step 3** — `feat(feed): keep reading position and show new-articles pill on foreground refresh`
  - `FeedUiState.newArticles`、ViewModel 新方法、`isFeedAtTop`、移除自動 `scrollToItem(0)`、`NewArticlesPill`、plurals、ViewModel / FeedScreenState 測試；模擬器驗證 F1。
- **Step 4** — `feat(domain): add location provider abstraction and weather place rules`
  - `Coordinates`、`LocationProvider`、`LocationResult`、`LocationFallbackReason`、`WeatherLocationRules`、`RefreshTrigger.LOCATION_CHANGED`、`FakeLocationProvider`、domain 測試。（`Weather` 尚未改，保持可編譯。）
- **Step 5** — `feat(data): add location manager provider and geocoder place names`
  - `AndroidLocationProvider`、`PlaceNameResolver` + Geocoder 實作、Hilt binding（此時尚未被使用）、Robolectric 測試。
- **Step 6** — `feat(data): migrate weather snapshot to schema v2 with location columns`
  - Entity v2、`MIGRATION_1_2`、`2.json`、`room-testing` + test assets、`Weather.place`（`WeatherPlace`）與 mapper、UI 暫時只顯示 `place.name ?: "Taipei"`；仍固定抓台北（`place` = TAIPEI fallback / PERMISSION_NOT_GRANTED）。`LineFeedDatabaseMigrationTest`、mapper / TestData 更新。
- **Step 7** — `feat(data): fetch weather for device location with taipei fallback`
  - `OpenMeteoApi` / remote data source 帶座標與 `timezone=auto`、`OfflineFirstWeatherRepository` 定位 + 地名、`SourceRefresher.isContextStale`、`DefaultFeedRefresher` 整合、repository / refresher / API 測試。
- **Step 8** — `feat(feed): add location permission flow to weather hero card`
  - Manifest 加 coarse 權限、`LocationPermissionStatus` + controller、rationale dialog、設定頁 intent、`WeatherLocationUi` 純函式、`WeatherHeroCard` 標示與按鈕、`isLocating`、`onLocationPermissionGranted`、字串、測試；模擬器驗證 F2。
- **Step 9** — `docs: document new-articles pill and location-based weather`
  - README（功能清單、測試數、已知限制：無 GMS 裝置可能取不到位置、跨 session 不記永久拒絕）、DECISIONS（偷看而非合併、LocationManager vs Fused、coarse only、Geocoder、只重建天氣表的 migration）、PLAN.md §1.3 把對應的延後項目改標為已完成。

---

## 5. 風險與注意事項

- **`MigrationTestHelper` 在 Robolectric 下讀 schema**：Room Gradle plugin 預設只把 schema 接到 androidTest assets；需手動把 `schemas` 加到 `test` assets。若 helper 仍無法在 Robolectric 運作，退而求其次：用 `SupportSQLiteOpenHelper` 以 `1.json` 的 `createSql` 建 v1 DB → 以 Room + `addMigrations` 開啟 → 斷言資料（仍是真的跑 migration），並在 NOTES 記錄原因。不可因此改用 destructive migration。
- **Robolectric 的 `LocationManagerCompat.getCurrentLocation`**：API 30+ 走平台 `getCurrentLocation`，`ShadowLocationManager` 需用 `simulateLocation` 餵資料，時序可能要 `shadowOf(Looper.getMainLooper()).idle()`；executor 用 `ContextCompat.getMainExecutor` 時尤其要注意。卡住時把 timeout 縮到毫秒並只驗證逾時路徑，其餘靠 fake 在 repository 層測。
- **coarse-only 在模擬器**：`adb emu geo fix` 餵的是 GPS；coarse 權限下需靠 fused provider（API 31+ Google APIs image）才拿得到。驗證時用 API 34/35 Google APIs 模擬器；取不到就是 `UNAVAILABLE` 流程，也算有驗到 fallback。
- **Paging + LazyColumn 錨點**：F1 在頂端原地刷新依賴「index 0 的 `weather` item key 永遠存在」。若之後有人把天氣卡移進 PagingData 或在它前面加 item，這個保證就消失——在 `FeedContent` 加註解說明。
- **OkHttp cache 讓「偷看」看到舊資料**：文章 TTL（20/60 分）大於 `max-age=600`，正常情況偷看一定打到網路；但若快取期間內有人觸發 check（例如 TTL 被調短），會回 cache 的舊第一頁 → 誤判 `UpToDate` 並 markSuccess。可接受（最多延遲 10 分鐘），在 KDoc 註明，不另加 `Cache-Control: no-cache`（那會讓點 pill 後的 REFRESH 無法重用這次回應）。
- **`combine` 參數數**：`uiState` 加 `newArticles` 後正好 5 個；再加欄位時要先把 `userMessage` + `newArticles` 併成一個 `MutableStateFlow<Ephemeral>`。
- **Geocoder 同步版在主執行緒會 ANR**：一定包在 IO dispatcher；API 33+ listener 版回呼執行緒不保證，用 `suspendCancellableCoroutine` 即可。
- **權限撤銷 → process 被殺**：這是系統行為不是 bug；別在 ViewModel 快取權限狀態。
- **範圍控制**：若時間不夠，Step 8 的 rationale dialog 與「開啟定位服務」按鈕可降級為純文字標示；F1（Step 1–3）與 F2 的資料流（Step 4–7）優先。
