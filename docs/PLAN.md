# LineFeed：架構與實作計畫

> 讀者：負責實作的 agent（Sonnet），以及寫 README / DECISIONS.md 時的作者本人。
> 原則：**每一步都能獨立 build、都有測試、都講得出理由**。遇到本文件沒寫清楚的地方，選最簡單、可測試的做法，並記在 commit message 或 `docs/NOTES.md`（實作期間的決策流水帳，最後彙整進 DECISIONS.md）。
>
> 本文件中的版本、API 欄位、payload 大小都在 2026-09-21 於本機實際驗證過（見 §6、§8 的「驗證證據」）。

---

## 0. 一頁摘要

| 項目 | 決定 |
|---|---|
| UI | Jetpack Compose + Material 3，自訂品牌綠色主題，支援 Dark theme（不用 dynamic color） |
| 架構 | 單向資料流（UDF）；Room 是唯一資料來源（single source of truth）；ViewModel 對外只暴露一個 `StateFlow<XxxUiState>` |
| DI | Hilt（KSP） |
| 持久化 | Room（文章快取、收藏快照、天氣、服務卡、同步 metadata 全部進同一個 DB）；**不使用 DataStore** |
| 網路 | Retrofit 3 + OkHttp 5 + kotlinx.serialization；OkHttp 磁碟快取（尊重 server `Cache-Control`） |
| 圖片 | Coil 3（共用 OkHttpClient）；收藏文章的圖片另外下載到 `filesDir`，保證離線可看 |
| 並行 | Coroutines + Flow；注入 `CoroutineDispatcher`、`@ApplicationScope CoroutineScope`、`AppClock` |
| 分頁 | **手寫 keyset 分頁**（`published_at_lte` 游標 + id 去重），不用 Paging 3。異質 feed 由純函式 `FeedAssembler` 組合 |
| 新鮮度 | 每個來源各自 TTL（天氣 15/30 分、文章 20/60 分、服務卡 12/24 小時，依 unmetered/metered），stale-while-revalidate，只在前景刷新，無背景同步 |
| Module | 8 個 module + `build-logic` convention plugins：`app`、`core:domain`(純 JVM)、`core:data`、`core:designsystem`、`core:testing`(純 JVM)、`feature:feed`、`feature:detail`、`feature:saved` |
| 工具鏈 | Gradle 9.7.1、AGP 9.4.0（內建 Kotlin）、Kotlin 2.4.20、KSP 2.3.12、compileSdk 37 / targetSdk 36 / minSdk 24、JDK 21 執行、bytecode target 17 |
| 測試 | JUnit4 + kotlinx-coroutines-test + Turbine + 手寫 Fake；Room/Repository 測試用 Robolectric（SDK 36）；網路解析用 MockWebServer + 真實 JSON fixture。**不用 MockK** |
| 指令 | Build：`./gradlew assembleDebug`；測試：`./gradlew unitTest`（root 聚合 task） |

---

## 1. 需求拆解與優先序

### 1.1 Must-have（全部要做）

| # | 需求 | 本專案對應 | 理由 / 備註 |
|---|---|---|---|
| M1 | Feed 分頁載入 | Spaceflight News 文章，keyset 分頁，接近底部自動載入下一頁 | 需求明文 |
| M2 | 詳情頁 | `feature:detail`：大圖、來源、時間、作者、標題、summary、「閱讀原文」 | Spaceflight API 只提供 summary，沒有全文（見 §4.4） |
| M3 | 收藏 / 取消收藏，首次載入後離線可讀 | `bookmarks` 表存完整快照 + 圖片存到 `filesDir` | 不能只存 id（feed 快取會被清） |
| M4 | 「已收藏」清單頁 | `feature:saved` | 需求明文 |
| M5 | 異質 feed（至少再一種真實來源、不同 cell） | Open-Meteo 天氣 hero 卡（置頂）+ DummyJSON 服務卡（穿插） | 兩種都免 key；做兩種來源而非一種，因為「兩種更新節奏不同的來源」才能展示新鮮度策略的差異（分鐘 vs 小時 vs 天） |
| M6 | 新鮮度策略 | `FreshnessPolicy` + `DefaultFeedRefresher`，README 說明推理 | 評分重點之一 |
| M7 | 所有 UI 狀態：loading / empty / error / offline | 每個畫面都有明確狀態表（§5） | 需求明文 |
| M8 | minSdk 24、Kotlin、一行指令 build、真實 commit history | §8、§10 | 基本規則 |
| M9 | README / DECISIONS.md / AI_USAGE.md / Plan & Sequencing / 已知限制 | 最後一個 commit | 繳交物 |

### 1.2 Nice-to-have（要做，依序）

| 優先 | 項目 | 理由 |
|---|---|---|
| N1 | **測試涵蓋快取 / 新鮮度 + 非同步邏輯** | 投報率最高：直接證明 M6 的推理是「被驗證過的」，且面試時可以拿測試當講稿 |
| N2 | **多 module** | 依賴方向由 build 強制（feature 碰不到 Room/Retrofit），且是 Senior 職缺常問話題 |
| N3 | **CI（GitHub Actions）** | 很便宜（一個 yml），而且越早加越有價值：之後每個 commit 都被驗證 → 放在第 2 個實作 commit |
| N4 | **Dark theme** | Material 3 幾乎零成本，只需要定義兩組 color scheme |
| N5 | 第二種異質來源（天氣 + 服務卡兩種都做） | 已併入 M5 |
| N6 | 本地搜尋 / 過濾（Saved 頁） | 選「離線可用、零流量」的版本，與新鮮度 / 省流量主題一致；排在最後，時間不夠第一個砍 |
| N7 | 輕量動畫：`Modifier.animateItem()`、Coil crossfade、收藏 icon 切換動畫 | 幾行程式碼；複雜轉場（shared element）不做 |

### 1.3 刻意延後 / 砍掉（README Plan & Sequencing ③ 的素材）

| 項目 | 決定 | 理由 |
|---|---|---|
| 電影卡（TMDB） | 砍 | 需要 API key → 面試官 clone 下來不能直接跑，違反「一行指令 build 起來就能用」的精神 |
| 背景定期同步（WorkManager） | 砍 | 使用者沒在看的時候刷新 = 浪費流量與電量；前景 SWR 已足夠讓 feed「打開就新鮮」。若要做：`PeriodicWorkRequest` + `NetworkType.UNMETERED` + `requiresCharging`，預抓第一頁 |
| 離線閱讀「原文全文」 | 砍 | API 只給 summary；抓第三方網頁做 readability 萃取 / WebView archive 屬於另一個產品題目，且有版權與流量問題。離線可讀範圍 = API 提供的全部欄位 + 圖片 |
| 依定位的天氣 | 砍（固定台北） | 需要定位權限與權限拒絕流程，對評分重點（新鮮度、離線）沒有幫助 |
| 遠端搜尋（API `search=`） | 延後 | 選擇本地過濾（離線可用）；遠端搜尋需要另一套分頁與 debounce 取消邏輯 |
| Paging 3 | 不採用（不是延後） | 見 §2.8 |
| 拆分 `core:network` / `core:database` | 延後 | 目前只有 `core:data` 一個消費者；等第二個消費者出現再拆（YAGNI） |
| 「N 則新文章」提示 pill（取代自動插入） | 延後 | 目前靠 LazyColumn 以 key 保持捲動錨點，已避免跳動 |
| Compose UI 測試 / 截圖測試 / instrumented test | 延後 | UI 狀態由 ViewModel 單元測試覆蓋（狀態 → 畫面是純渲染）；UI 測試回報率較低 |
| 漢堡選單 / Drawer | 砍 | 參考圖有，但沒有實際內容可放；放一個空選單比沒有更糟 |
| 取消收藏的 Undo snackbar | 延後 | 好的 UX，但非必要 |
| Baseline Profile、R8 release 設定、zh-TW 在地化 | 延後 | 作業範圍外；字串全部放 `strings.xml`，之後加 `values-zh-rTW` 即可 |
| 使用者可調的 Data Saver 設定頁 | 延後 | `TtlConfig` 已抽象化，之後加設定只是換一組 TTL |

---

## 2. 架構決策（DECISIONS.md 素材）

每一項格式：**選擇 → 替代方案 → 取捨**。

### 2.1 UI：Jetpack Compose + Material 3
- **選擇**：Compose、Material 3（`PullToRefreshBox`、`NavigationBar`、`Card`）、`LazyColumn` 搭配 `key` + `contentType`。
- **替代**：Views + RecyclerView（`ListAdapter` + 多 ViewType）。
- **取捨**：異質 cell 在 Compose 裡就是 `when (item)`，不用寫 ViewHolder / DiffUtil；狀態驅動 UI 與 UDF 天然契合；Dark theme 零成本。代價是 Compose 效能陷阱（不穩定參數造成重組）——Kotlin 2.x 預設 strong skipping，且 `LazyColumn` 使用穩定 key，足以應付此規模。Views 在大型既有專案（LINE 主 App）仍常見，面試時可說明「新專案選 Compose，既有 Views 專案會用 `ComposeView` 漸進導入」。

### 2.2 狀態管理：UDF + 單一 `StateFlow<UiState>`
- **選擇**：
  - 每個 ViewModel 對外只有 `val uiState: StateFlow<XxxUiState>` 與若干 `fun onXxx()` 事件方法。
  - `uiState` = `combine(repository flows…, 本地 MutableStateFlow)` → `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`。
  - 一次性訊息（snackbar）**放在 state 裡**（`userMessage: UserMessage?` + `onUserMessageShown()`），不用 `Channel`/`SharedFlow`——config change / 背景時不會遺失，也容易測試（Google 官方 UI layer 指引的做法）。
  - 「是否整頁顯示 loading/empty/error/offline」由**純函式** `deriveFullScreenState(...)` 推導，單獨測試。
- **替代**：MVI 框架（Orbit / Mavericks）、多個獨立 `StateFlow`、`LiveData`、Compose `mutableStateOf` 放在 VM。
- **取捨**：單一 state 物件讓「不可能狀態」較難出現、測試只需斷言一個值；代價是每次任何欄位變動都複製整個 data class（此規模可忽略）。不引入 MVI 框架以避免額外概念與依賴。

### 2.3 DI：Hilt
- **選擇**：Hilt + KSP；`@HiltViewModel`；`@Binds` 綁定 domain interface → data 實作；qualifier：`@IoDispatcher`、`@ApplicationScope`、`@SpaceflightRetrofit` / `@OpenMeteoRetrofit` / `@DummyJsonRetrofit`。
- **替代**：Koin（runtime 解析，錯誤在執行期才爆）、手動 DI（`AppContainer`，ViewModel factory 樣板多）、kotlin-inject / Metro（新但生態較小）。
- **取捨**：編譯期驗證圖、Android 標準、與 Navigation/ViewModel 整合；代價是 KSP 編譯時間與註解樣板。**單元測試不使用 Hilt**：所有 class 都是 constructor injection，測試直接 `new` 並傳入 fake，因此不需要 `hilt-android-testing`。

### 2.4 持久化：Room（唯一 DB），不用 DataStore
- **選擇**：Room 2.8.5，一個 `LineFeedDatabase`，5 張表（§6.4）。
- **替代**：DataStore（存 timestamp）、SQLDelight、檔案 JSON 快取、只靠 OkHttp HTTP cache。
- **取捨**：
  - 「資料寫入」與「lastSuccessAt 更新」必須**在同一個 transaction**，否則會出現「時間戳說新鮮但資料是舊的」；若 metadata 放 DataStore 就做不到原子性 → 這是 metadata 也進 Room 的關鍵理由。
  - Room 的 `Flow` 查詢讓 UI 自動反映快取變化（SSOT）。
  - 只靠 HTTP cache 無法做「過期資料仍顯示」與收藏離線保證。
  - Schema 匯出到 `core/data/schemas/`（commit 進 repo）。**不使用 `fallbackToDestructiveMigration`**：收藏是使用者資料，升版必須寫 migration（目前 version 1）。

### 2.5 網路：Retrofit 3 + OkHttp 5 + kotlinx.serialization
- **選擇**：三個 base URL → 三個 Retrofit instance（qualifier），共用一個 `OkHttpClient`（10 MB 磁碟 `Cache`、timeout、debug 才加 `HttpLoggingInterceptor`）。JSON：`Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }`，converter 用官方 `converter-kotlinx-serialization`。
- **替代**：Ktor client（KMP 友善但設定多）、Moshi（需 KSP codegen 或 reflection）、Gson（不理解 Kotlin null-safety）。
- **取捨**：Retrofit 在 Android 業界最普遍；kotlinx.serialization 無反射、Kotlin-first、編譯期產生 serializer，也被 Navigation type-safe route 使用（同一套工具）。Retrofit service 被包在 `XxxRemoteDataSource` interface 後面，repository 測試用 fake remote，不必起 server；Retrofit 介面本身另用 MockWebServer 測 URL/參數/解析。

### 2.6 圖片載入：Coil 3
- **選擇**：`coil-compose` + `coil-network-okhttp`（共用 OkHttpClient），Application 實作 `SingletonImageLoader.Factory`：記憶體快取 25%、磁碟快取 100 MB、crossfade。
- **替代**：Glide（成熟，但 Compose 整合是次要）、Fresco。
- **取捨**：Coil 是 Kotlin/coroutines/Compose-first，API 小。**但 Coil 磁碟快取是 LRU、可被清除**，不能作為收藏離線的保證 → 收藏圖片另存 `filesDir`（§4.2）。

### 2.7 並行模型：Coroutines + Flow
- **選擇**：
  - Repository 對外：`Flow<T>`（觀察快取）+ `suspend fun`（一次性動作）。
  - 注入 `@IoDispatcher CoroutineDispatcher`（只用在檔案 IO，例如收藏圖片寫檔；Room/Retrofit 的 suspend API 已是 main-safe，不需包 `withContext`，這點要在 DECISIONS 講清楚，避免「到處 withContext(IO)」的反模式）。
  - `@ApplicationScope CoroutineScope`（`SupervisorJob() + Dispatchers.Default`）：給不該隨畫面取消的工作——前景刷新、收藏圖片下載、single-flight 共享工作。
  - `AppClock` interface 注入現在時間；`NetworkMonitor` interface 注入網路狀態。
  - **Single-flight**：同一來源同時被觸發多次刷新（例如回前景 + 網路恢復同時發生）時，只打一次網路，其餘呼叫者 await 同一個結果（`SingleFlight<K, V>` 小工具，§7）。
  - 自訂 `suspendRunCatching {}`：捕捉例外但**重新拋出 `CancellationException`**（`runCatching` 會吞掉取消，是常見 bug）。
- **替代**：RxJava（學習成本、與 Compose/Room 整合不如 Flow）、callback。
- **取捨**：Flow 是 Room/Compose/Lifecycle 的原生語言；結構化並行讓取消正確傳遞。

### 2.8 分頁：手寫 keyset 分頁（不用 Paging 3）
- **選擇**：
  - Room 觀察整個快取文章列表 `Flow<List<ArticleEntity>>`（排序 `publishedAt DESC, id DESC`）。
  - 下一頁：`loadNextPage()` 以快取中**最舊的 `publishedAt`** 當游標，呼叫 `?published_at_lte=<cursor>&ordering=-published_at&limit=20`，用 id upsert 去重（邊界那幾筆一定會重複回來）。
  - 結束條件：回傳筆數 < pageSize，或「這一頁沒有任何新 id」（防止同一秒大量文章造成無限迴圈）。
  - ViewModel 持有 `AppendState`（Idle / Loading / Error / EndReached / Offline），UI 透過 `snapshotFlow { lastVisibleIndex }` 在距離底部 5 筆時呼叫 `onNearEnd()`；VM 在 Loading/EndReached 時忽略。
  - 異質 feed：純函式 `FeedAssembler.assemble(weather, articles, services): List<FeedItem>`。
- **為什麼用 keyset 而非 offset**：文章持續新增，offset 分頁在「讀第 2 頁之前有新文章發布」時會重複或漏掉；已驗證 API 支援 `published_at_lte` / `published_at_lt`（§6.1）。
- **替代**：Paging 3 + `RemoteMediator` + Room `PagingSource`。
- **取捨**：
  - Paging 3 的優點：記憶體視窗化、placeholder、內建 LoadState、retry。
  - 不採用的理由：(1) 異質混排在 Paging 裡只能用 `insertSeparators`，而「每 N 篇插一張服務卡」需要位置資訊，`insertSeparators` 只有 before/after，沒有 index，要嘛在 DB 維護 sortIndex、要嘛寫有狀態的 separator，都很彆扭；(2) `RemoteMediator.initialize()` 與 `REFRESH` 會變成第二個「決定何時刷新」的地方，和我們集中式的新鮮度協調器（§3）重複；(3) 本 App 快取上限數百筆，Paging 的記憶體視窗化效益低；(4) 手寫版本讓 feed 組合成為可完整單元測試的純函式，append 狀態機也清楚可測（對應加分項「非同步邏輯測試」）。
  - 代價：自己處理觸發時機、去重、重試、結束條件；沒有 placeholder。若 feed 規模變成無限長（例如 VOOM），會改回 Paging 3 並把服務卡改成 DB 內的一種 row type。

### 2.9 Module 結構
- **選擇**（8 module + included build `build-logic`）：

```mermaid
graph TD
  app --> feature_feed[feature:feed]
  app --> feature_detail[feature:detail]
  app --> feature_saved[feature:saved]
  app --> core_data[core:data]
  app --> core_designsystem[core:designsystem]
  feature_feed --> core_domain[core:domain]
  feature_feed --> core_designsystem
  feature_detail --> core_domain
  feature_detail --> core_designsystem
  feature_saved --> core_domain
  feature_saved --> core_designsystem
  core_data --> core_domain
  core_testing[core:testing] --> core_domain
  feature_feed -. testImplementation .-> core_testing
  feature_detail -. testImplementation .-> core_testing
  feature_saved -. testImplementation .-> core_testing
  core_data -. testImplementation .-> core_testing
```

  - `core:domain`：**純 Kotlin/JVM**。domain model、repository interface、`FreshnessPolicy`、`AppClock`、`NetworkMonitor` interface、`AppError`。沒有 Android 依賴 → 由 build 系統保證 domain 邏輯可在 JVM 上快速測試。
  - `core:data`：Android library。網路（Retrofit/DTO）、資料庫（Room）、repository 實作、刷新協調器、`ConnectivityNetworkMonitor`、Hilt module。實作 class 都是 `internal`。
  - `core:designsystem`：theme（light/dark）、共用 composable（狀態畫面、離線 banner、收藏按鈕、網路圖片）、`RelativeTimeFormatter`。
  - `core:testing`：**純 JVM**。所有 fake（repository、clock、network monitor）+ `MainDispatcherRule`，放在 `main` source set 讓各 module 以 `testImplementation` 共用。
  - `feature:*`：只依賴 `core:domain` + `core:designsystem`，**看不到 Room / Retrofit**（`core:data` 不在其 classpath）。
  - `app`：`Application`、`MainActivity`、NavHost、底部導覽、前景刷新觸發。
- **替代**：(a) 單一 `app` module 以 package 分層；(b) Now-in-Android 完整版（`core:network`、`core:database`、`core:datastore`、`core:model`、`core:ui`… 十幾個 module）。
- **取捨**：(a) 最簡單，但依賴方向只靠紀律；(b) 對一週作業過度，build 設定成本高、每個 module 都要樣板。選中間值：切在「真正有價值的邊界」——domain 純 JVM、feature 與資料實作隔離、測試 fake 共用。網路與 DB 暫不拆（只有一個消費者）。以 convention plugins 消除重複設定，每個 module 的 `build.gradle.kts` 只剩幾行。

### 2.10 導覽
- **選擇**：Navigation Compose 2.10.1 type-safe routes（`@Serializable data object FeedRoute`、`@Serializable data class ArticleDetailRoute(val articleId: Long)`）。每個 feature 提供 `NavGraphBuilder.feedScreen(onArticleClick: (Long) -> Unit)` 之類的 extension；只有 `app` 知道所有 route，feature 之間不互相依賴。
- **替代**：Navigation 3（較新、API 仍在演進）、Compose Destinations（第三方）。
- **注意**：ViewModel 讀參數用 `savedStateHandle.get<Long>("articleId")`，**不要用 `savedStateHandle.toRoute<>()`**——後者需要 Android `Bundle`，純 JVM 單元測試會失敗。

### 2.11 主題
- 品牌綠 `#06C755` 為 primary seed，定義 light / dark 兩組 `ColorScheme`；跟隨系統深色模式。
- **刻意不用 dynamic color（Material You）**：內容型 App 的品牌識別比桌布配色重要；這是可討論的取捨。

---

## 3. 新鮮度（Freshness）策略（README 核心章節）

### 3.1 先看數據（2026-09-21 實測）

| 請求 | gzip 後大小 |
|---|---|
| Spaceflight 文章 1 頁（20 筆） | ~4.9 KB |
| Open-Meteo 台北 current + 7 日 | ~0.4 KB |
| DummyJSON 10 筆（**有** `select=`） | ~1.4 KB（沒 `select` 是 3.4 KB） |
| **一次完整刷新三個來源** | **~6.7 KB** |
| 一張 Spaceflight 文章圖 | ~100 KB |

- 文章發布頻率：最近 100 篇跨越約 8 天 → **約每天 12 篇、每小時 0.5 篇**。
- Open-Meteo `current.interval = 900` → 天氣資料**每 15 分鐘**才更新一次。
- Spaceflight 回應帶 `Cache-Control: max-age=600`；DummyJSON 帶 `ETag`、rate limit 100。

**結論（README 要寫的推理）**：JSON 很小，真正吃流量的是**圖片**。因此新鮮度策略的目標是：
1. 不做「不可能拿到新資料」的請求（比來源更新頻率更頻繁地刷新毫無意義）；
2. 刷新不要造成**圖片重新下載**（保留快取、以 id 合併而非整批替換、Coil 磁碟快取）；
3. 使用者沒在看就不刷新（無背景同步）；
4. 使用者明確要求（下拉）時永遠尊重。

### 3.2 定義「新鮮」

> 某來源的快取是「新鮮」的 ⇔ `now - lastSuccessAt < ttl(source, networkType)`。
> 「過期（stale）」的資料**仍然顯示**（stale-while-revalidate），同時在背景刷新；
> 「過舊（outdated）」只影響 UI 標示（例如天氣卡顯示「3 小時前更新」警示色），不影響是否顯示。

| 來源 | TTL（unmetered / Wi-Fi） | TTL（metered / 行動網路） | outdated 標示門檻 | 理由 |
|---|---|---|---|---|
| 天氣（Open-Meteo） | 15 分 | 30 分 | 3 小時 | 資料源本身 15 分更新；天氣是「現在」的資訊，最需要新 |
| 文章（Spaceflight，第一頁） | 20 分 | 60 分 | 12 小時 | 每小時約 0.5 篇；server 自己宣告 max-age 10 分 |
| 服務卡（DummyJSON） | 12 小時 | 24 小時 | — | 推廣內容以「天」為單位變動；另可避免 rate limit |

- Metered 網路 TTL 較長（省流量），unmetered 較短（便宜，換取更新鮮）。
- 下一頁（append）**不受 TTL 管**：使用者往下捲就是明確意圖，只要在線就抓。
- TTL 集中在 `TtlConfig`，之後若加「Data Saver」設定只是換一組值。

### 3.3 何時觸發刷新

| 觸發（`RefreshTrigger`） | 行為 |
|---|---|
| `COLD_START`（程序第一次進入前景） | 立即顯示快取；逐來源判斷 TTL，只刷新過期的 |
| `FOREGROUND`（從背景回前景，`ProcessLifecycleOwner` ON_START） | 同上。TTL 本身就是節流器：5 分鐘內切換 App 十次也不會多打請求 |
| `NETWORK_RESTORED`（前景中 offline → online） | 同上（離線期間可能已過期） |
| `USER_PULL`（下拉重新整理 / 錯誤頁的重試） | **忽略 TTL，所有來源都刷新**（不論 metered），因為這是使用者明確意圖 |
| 背景中的任何事件 | 不刷新 |

觸發流由純 Flow 函式 `refreshTriggers(isForeground: Flow<Boolean>, network: Flow<NetworkStatus>): Flow<RefreshTrigger>` 產生（可用 Turbine 測），`app` 只負責把 `ProcessLifecycleOwner` 轉成 `Flow<Boolean>` 並在 `@ApplicationScope` 收集。

### 3.4 決策規則（`FreshnessPolicy.evaluate`，純函式）

依序判斷：
1. 網路 `OFFLINE` → `Skip(OFFLINE)`（即使是 `USER_PULL`；UI 另外顯示離線提示）
2. `USER_PULL` → `Fetch`
3. `lastSuccessAt == null`（從未成功）→ `Fetch`
4. `lastSuccessAt > now`（使用者調過系統時間）→ `Fetch`（視為過期，避免永遠不刷新）
5. `now - lastSuccessAt >= ttl` → `Fetch`；否則 `Skip(FRESH)`

### 3.5 避免浪費流量的其他手段

- **Single-flight**：同一來源同時多個觸發只打一次（§2.7）。
- **文章刷新採「重疊合併」而非整批替換**：刷新抓第一頁後，若該頁與快取有重疊 id → 只 upsert（保留使用者已載入的舊頁、圖片也不必重抓）；若沒有重疊（離線太久，中間有缺口）→ 在 transaction 中清空文章快取再寫入（避免列表出現時間斷層）。快取上限 300 筆，超過從最舊的刪。
- **失敗不破壞快取**：網路失敗時快取與 `lastSuccessAt` 都不動，只記錄 `lastAttemptAt/lastError`。
- **OkHttp HTTP cache**：尊重 `max-age=600`；DummyJSON 的 `ETag` 讓條件請求可以回 304。
- **DummyJSON 使用 `select=`**：payload 減少約 60%。
- **圖片**：只載入可見項目（Coil 預設行為）、100 MB 磁碟快取、`crossfade`；列表縮圖用固定尺寸 `size` 讓 Coil 解碼較小 bitmap（省記憶體，流量仍取決於來源圖檔——Spaceflight 只提供單一尺寸，README 已知限制要寫）。
- **無背景同步**（§1.3）。

### 3.6 可測試性設計

- `AppClock { fun now(): Instant }` → 測試用 `FakeClock`（可 `advanceBy(Duration)`）。
- `NetworkMonitor { val status: Flow<NetworkStatus> }` → 測試用 `FakeNetworkMonitor`（`MutableStateFlow`）。
- `FreshnessPolicy` 是純函式、`refreshTriggers` 是純 Flow 函式、`DefaultFeedRefresher` 只依賴 interface → 全部 JVM 單元測試。
- 所有時間用 `java.time.Instant/Duration/LocalDate`（Android module 開啟 core library desugaring 以支援 minSdk 24）。

---

## 4. 離線與收藏

### 4.1 資料模型關係

```
feed_articles  (可拋棄的快取；刷新可能清空、超過 300 筆會修剪)
     │  id 相同時，UI 以 bookmarkedIds 標示「已收藏」
     ▼
bookmarks      (使用者資料；完整快照 + localImagePath；永不因刷新而刪除)
```

- **收藏 ≠ feed 快取上的布林欄位**。理由：feed 快取會被清空/修剪；若收藏只是 flag，清快取就會失去收藏內容。
- 收藏時複製文章完整欄位到 `bookmarks`（title、summary、newsSite、url、imageUrl、authors、publishedAt、savedAt）。
- Feed 顯示收藏狀態：`combine(feedDao.observeAll(), bookmarkDao.observeIds())` → `Article.isBookmarked`。
- 詳情頁資料來源優先序：`bookmarks` 快照 → `feed_articles`（`observeArticle(id)` 以 `combine` 兩個 DAO flow 實作）。因此從 Saved 點進去、即使 feed 快取已清空，也能離線閱讀。
- 取消收藏：刪 row + 刪本地圖片檔。

### 4.2 圖片離線保證

- 收藏時在 `@ApplicationScope` 啟動下載：OkHttp 抓 `imageUrl` → 寫入 `filesDir/bookmark_images/{articleId}`（先寫 `.tmp` 再 rename，避免半檔）→ 更新 `bookmarks.localImagePath`。
- 下載失敗（離線收藏）：`localImagePath` 維持 null；之後每次 `NETWORK_RESTORED`/`FOREGROUND` 呼叫 `retryPendingImageDownloads()`。
- UI 載入圖片：`localImagePath?.let(::File) ?: imageUrl`，兩者都沒有時顯示 placeholder。
- Metered 也下載：單張圖（~100 KB）且是使用者明確動作。
- 圖片下載器抽象為 `internal interface ImageDownloader { suspend fun download(url: String, target: File) }`，測試用 fake。

### 4.3 離線時各功能

| 功能 | 離線行為 |
|---|---|
| Feed | 顯示快取（文章 + 天氣 + 服務卡）+ 離線 banner（含「最後更新 X 前」）；append footer 顯示「離線中，連線後可載入更多」 |
| 詳情 | 完整顯示（快取或收藏快照）；「閱讀原文」按鈕 disabled 並提示離線 |
| 收藏 / 取消收藏 | 完全可用（純本地寫入）；圖片待連線後補抓 |
| Saved | 完整可用；頂部 banner「You're offline — showing saved items」 |
| 搜尋（Saved 過濾） | 完全可用（本地 SQL） |

### 4.4 「內文」的範圍
Spaceflight API 只有 `summary`（已驗證 detail endpoint 欄位與 list 相同，沒有 content）。詳情頁顯示 summary 作為內文，並提供「閱讀原文」以 `Intent.ACTION_VIEW` 開瀏覽器。README 已知限制要寫。

---

## 5. UI 狀態

### 5.1 共用元件（`core:designsystem`）
- `FullScreenMessage(icon, title, body, actionLabel?, onAction?)`：Loading（`CircularProgressIndicator` 或 skeleton）、Empty、Error、Offline 共用版型。
- `OfflineBanner(text)`：頂部細長條，`AnimatedVisibility`。
- `BookmarkIconButton(isBookmarked, onToggle)`：實心/空心切換 + contentDescription。
- `FeedImage(model, contentDescription, modifier)`：包 `AsyncImage`，統一 placeholder / error 圖。

### 5.2 Feed（Reading）

| 條件 | 呈現 |
|---|---|
| 沒有快取、正在刷新 | 整頁 Loading（skeleton 3 張卡） |
| 沒有快取、離線 | 整頁 Offline：「目前離線，無法載入最新內容」+ 按鈕「前往已收藏」；恢復連線時自動刷新（`NETWORK_RESTORED`） |
| 沒有快取、刷新失敗 | 整頁 Error：錯誤訊息（依 `AppError` 類型）+「重試」（`USER_PULL`） |
| 沒有快取、刷新成功但 0 筆 | 整頁 Empty +「重新整理」 |
| 有快取 | 列表。背景 SWR 刷新**不顯示大轉圈**，只有 `USER_PULL` 顯示 pull-to-refresh 指示器 |
| 有快取、刷新失敗 | 保留列表，snackbar「無法更新，顯示 X 前的內容」 |
| 有快取、離線 | 保留列表 + OfflineBanner |
| Append Loading / Error / EndReached / Offline | 底部 footer：小轉圈 / 「載入失敗・重試」/「已經到底了」/「離線中」 |
| 天氣卡 | 獨立狀態：有資料（過舊時顯示「X 前更新」警示）/ 無資料且載入中（skeleton）/ 無資料且失敗（精簡的「天氣暫時無法取得」列，不擋文章） |
| 服務卡 | 沒資料就不插入（不顯示錯誤；它是次要內容） |

`deriveFullScreenState(hasArticles, articlesInFlight, lastArticlesResult, network): FullScreenState?` 規則：
`hasArticles → null`；`network == OFFLINE → Offline`；`articlesInFlight || lastArticlesResult == null → Loading`；`lastArticlesResult is Failed → Error(error)`；否則 `Empty`。

### 5.3 Detail

| 條件 | 呈現 |
|---|---|
| 讀取中（DB 查詢，通常 <1 frame） | 空白 + 延遲 300ms 才顯示轉圈（避免閃爍；可用 `LaunchedEffect` + `delay`） |
| 找不到（id 不在快取也不在收藏） | Error「找不到這篇文章」+ 返回 |
| 內容 | 大圖（本地優先）、來源 chip、相對時間、作者、標題、summary、收藏按鈕（top bar）、「閱讀原文」 |
| 離線 | 內容照常；「閱讀原文」disabled + 說明文字 |

### 5.4 Saved

| 條件 | 呈現 |
|---|---|
| 無收藏 | Empty：「還沒有收藏」+ 說明「在文章上點書籤即可稍後離線閱讀」 |
| 有收藏 | 列表（savedAt DESC）：來源 · 相對時間、標題、右側縮圖、實心書籤（點擊取消收藏） |
| 搜尋無結果 | Empty 變體：「找不到符合 "xxx" 的收藏」 |
| 離線 | 頂部 banner「You're offline — showing saved items」（對應參考圖） |
| Error | Room 讀取失敗極少見，不設計專屬畫面（`catch` 後記 log 並顯示 Empty）——DECISIONS 註記 |

### 5.5 UiState 型別

```kotlin
// feature:feed
data class FeedUiState(
    val items: List<FeedItem> = emptyList(),
    val fullScreen: FullScreenState? = FullScreenState.Loading,
    val isUserRefreshing: Boolean = false,
    val appendState: AppendState = AppendState.Idle,
    val isOffline: Boolean = false,
    val lastUpdated: Instant? = null,
    val userMessage: UserMessage? = null,
)
sealed interface FullScreenState { data object Loading; data object Empty; data object Offline; data class Error(val error: AppError) }
sealed interface AppendState { data object Idle; data object Loading; data object EndReached; data object Offline; data class Error(val error: AppError) }
sealed interface FeedItem {
    val key: String; val contentType: String
    data class WeatherHero(val state: WeatherCardState)            // key = "weather"
    data class TopStory(val article: Article)                      // key = "article-{id}"
    data class ArticleRow(val article: Article)                    // key = "article-{id}"
    data class Service(val card: ServiceCard, val slot: Int)       // key = "service-{id}-{slot}"
}
sealed interface WeatherCardState { data object Loading; data object Unavailable; data class Available(val weather: Weather, val isOutdated: Boolean) }

// feature:detail
sealed interface DetailUiState { data object Loading; data object NotFound; data class Content(val article: Article, val isOffline: Boolean) }

// feature:saved
data class SavedUiState(val query: String = "", val items: List<SavedArticle> = emptyList(), val isOffline: Boolean = false, val isLoading: Boolean = true)
```
（`UserMessage` 放在 `core:designsystem` 或各 feature：`data class UserMessage(val id: Long, @StringRes val messageRes: Int, val formatArgs: List<Any> = emptyList())`。）

---

## 6. 資料模型與 API 對應

### 6.1 Spaceflight News API v4（已 curl 驗證）

- 列表：`GET https://api.spaceflightnewsapi.net/v4/articles/`
  - 第一頁：`?limit=20&ordering=-published_at`
  - 下一頁：`?limit=20&ordering=-published_at&published_at_lte=<最舊 publishedAt ISO-8601>`（已驗證有效；`published_at_lt` 也可用）
  - 回應：`{ count, next, previous, results: [...] }`（`next` 為 offset URL，我們**不用**）
- 單篇：`GET /v4/articles/{id}/`（欄位與列表相同；本專案不需要呼叫，列為可用但未用）
- 欄位（results[]）：`id: Long`、`title`、`authors: [{name, socials}]`、`url`、`image_url`、`news_site`、`summary`、`published_at`（`2026-09-21T14:00:00Z`）、`updated_at`（**含微秒** `2026-09-18T23:10:20.648543Z`）、`featured`、`launches`、`events`
- 觀察到的陷阱：
  - **`published_at` 可能在未來**（實測有 14:00Z 發布、當下才 02:xxZ 的文章）→ `RelativeTimeFormatter` 對未來時間顯示絕對日期，不可顯示負數。
  - **同一秒多篇**（實測兩篇 `14:00:00Z`）→ 游標用 `lte` + id 去重 + 「無新 id 即結束」保護。
  - `summary` 可能很短（如 "From the ESA Blogs."）或空字串 → UI 空字串時隱藏摘要區。
  - `image_url` 保守視為 nullable / 可能為空字串 → mapper 轉成 `null`。

DTO：
```kotlin
@Serializable data class ArticleListResponseDto(val count: Int = 0, val next: String? = null, val results: List<ArticleDto> = emptyList())
@Serializable data class ArticleDto(
    val id: Long, val title: String, val authors: List<AuthorDto> = emptyList(), val url: String,
    @SerialName("image_url") val imageUrl: String? = null, @SerialName("news_site") val newsSite: String = "",
    val summary: String = "", @SerialName("published_at") val publishedAt: String, @SerialName("updated_at") val updatedAt: String? = null,
    val featured: Boolean = false,
)
@Serializable data class AuthorDto(val name: String)
```
Retrofit：
```kotlin
interface SpaceflightApi {
    @GET("v4/articles/") suspend fun getArticles(
        @Query("limit") limit: Int,
        @Query("ordering") ordering: String = "-published_at",
        @Query("published_at_lte") publishedAtLte: String? = null,
    ): ArticleListResponseDto
}
```

### 6.2 Open-Meteo（已 curl 驗證）

- `GET https://api.open-meteo.com/v1/forecast?latitude=25.0330&longitude=121.5654&current=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=Asia%2FTaipei&forecast_days=7`
- 回應重點：
  - `timezone: "Asia/Taipei"`、`utc_offset_seconds: 28800`
  - `current: { time: "2026-09-21T10:15"（**本地時間、無時區**）, interval: 900, temperature_2m: 29.9, weather_code: 0, is_day: 1 }`
  - `daily: { time: ["2026-09-21", ...], weather_code: [...], temperature_2m_max: [...], temperature_2m_min: [...] }`（**平行陣列**，mapper 要 zip 並檢查長度一致）
- DTO：`ForecastResponseDto(timezone: String, utcOffsetSeconds: Int, current: CurrentDto, daily: DailyDto)`；`CurrentDto(time: String, temperature2m: Double, weatherCode: Int, isDay: Int)`；`DailyDto(time: List<String>, weatherCode: List<Int>, temperature2mMax: List<Double>, temperature2mMin: List<Double>)`（`@SerialName` 對應 snake_case）。
- WMO code → `WeatherCondition`：0 `CLEAR`；1、2 `PARTLY_CLOUDY`；3 `CLOUDY`；45、48 `FOG`；51–57 `DRIZZLE`；61–67 `RAIN`；71–77 `SNOW`；80–82 `SHOWERS`；85–86 `SNOW`；95–99 `THUNDERSTORM`；其他 `UNKNOWN`。
- 顯示：城市 "Taipei"、目前溫度、狀態文字、今日 H/L（`daily[0]`）、之後 5 天（過濾掉早於「今天（Asia/Taipei，以 `AppClock` 計）」的日期——快取過夜時很重要）。

### 6.3 DummyJSON（已 curl 驗證）

- `GET https://dummyjson.com/products?limit=10&select=title,description,price,discountPercentage,rating,thumbnail,category,brand`
- 回應：`{ products: [{ id, title, description, price, discountPercentage, rating, thumbnail(.webp), category, brand? }], total, skip, limit }`（`brand` 可能缺，要 nullable）
- DTO：`ProductListResponseDto(products: List<ProductDto>)`、`ProductDto(id: Long, title, description, price: Double, discountPercentage: Double = 0.0, rating: Double = 0.0, thumbnail: String? = null, category: String = "", brand: String? = null)`
- Domain 映射成「在地服務/優惠卡」：`title`、`imageUrl = thumbnail`、`description`（截 2 行）、`ctaLabel = "Get ${discount.roundToInt()}% off"`（discount < 1 時用 "Open"）、`actionUrl = "https://dummyjson.com/products/{id}"`（目標動作：`ACTION_VIEW` 開啟；README 註明為 demo 目標）。

### 6.4 Domain model（`core:domain`）

```kotlin
data class Article(val id: Long, val title: String, val summary: String, val newsSite: String, val url: String,
                   val imageUrl: String?, val publishedAt: Instant, val authors: List<String>, val isBookmarked: Boolean)
data class SavedArticle(val article: Article, val savedAt: Instant, val localImagePath: String?)
data class Weather(val locationName: String, val current: CurrentWeather, val daily: List<DailyForecast>, val fetchedAt: Instant)
data class CurrentWeather(val temperatureC: Double, val condition: WeatherCondition, val isDay: Boolean, val observedAt: LocalDateTime)
data class DailyForecast(val date: LocalDate, val condition: WeatherCondition, val maxC: Double, val minC: Double)
enum class WeatherCondition { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, SHOWERS, THUNDERSTORM, UNKNOWN; companion object { fun fromWmo(code: Int): WeatherCondition } }
data class ServiceCard(val id: Long, val title: String, val description: String, val imageUrl: String?, val ctaLabel: String, val actionUrl: String)
enum class NetworkStatus { OFFLINE, METERED, UNMETERED }
enum class ContentSource { WEATHER, ARTICLES, SERVICES }
enum class AppError { OFFLINE, TIMEOUT, SERVER, PARSE, UNKNOWN }
```

### 6.5 Room entity（`core:data`，DB version 1）

| 表 | 欄位 | 主鍵 / 索引 | 說明 |
|---|---|---|---|
| `feed_articles` | id, title, summary, newsSite, url, imageUrl?, publishedAtMillis, updatedAtMillis?, authors (以 `\u001F` 串接的 String，或 TypeConverter), featured, fetchedAtMillis | PK id；index(publishedAtMillis) | 可拋棄快取 |
| `bookmarks` | articleId, title, summary, newsSite, url, imageUrl?, localImagePath?, publishedAtMillis, authors, savedAtMillis | PK articleId；index(savedAtMillis) | 使用者資料，完整快照 |
| `weather_snapshot` | locationKey ("taipei"), locationName, tempC, weatherCode, isDay, observedAtLocal (String), dailyJson (String, kotlinx.serialization), fetchedAtMillis | PK locationKey | daily 用 JSON 欄位：永遠整批讀寫，不需要 SQL 查詢 → 不值得另開表 |
| `service_cards` | id, title, description, imageUrl?, ctaLabel, actionUrl, position | PK id | 刷新時整批替換（transaction） |
| `sync_metadata` | source (TEXT, ContentSource.name), lastSuccessAtMillis?, lastAttemptAtMillis?, lastError? | PK source | 與資料在同一 transaction 更新 |

時間在 entity 一律存 `Long`（epoch millis），mapper 轉 `Instant`——避免 TypeConverter 隱式行為，也讓 SQL 排序直觀。

DAO 關鍵查詢：
- `FeedArticleDao`：`observeAll(): Flow<List<FeedArticleEntity>>`（ORDER BY publishedAtMillis DESC, id DESC）、`observeById(id)`、`upsertAll(list)`、`deleteAll()`、`existingIds(ids: List<Long>): List<Long>`、`oldestPublishedAt(): Long?`、`count()`、`trimTo(max: Int)`（`DELETE WHERE id NOT IN (SELECT id ... ORDER BY ... LIMIT :max)`）。
- `BookmarkDao`：`observeAll(query: String): Flow<List<BookmarkEntity>>`（`WHERE title LIKE '%'||:q||'%' ESCAPE '\' OR newsSite LIKE ...` ORDER BY savedAtMillis DESC；呼叫端先 escape `%` `_` `\`）、`observeIds(): Flow<List<Long>>`、`observeById(id)`、`upsert`、`delete(id)`、`pendingImageDownloads(): List<BookmarkEntity>`、`updateLocalImagePath(id, path)`。
- `WeatherDao`、`ServiceCardDao`、`SyncMetadataDao`（`observeAll(): Flow<List<SyncMetadataEntity>>`、`get(source)`、`markSuccess(source, at)`、`markFailure(source, at, error)`）。

Mapper（`core:data/.../mapper/`，全部是 top-level pure function，逐一單元測試）：`ArticleDto.toEntity(fetchedAt)`、`FeedArticleEntity.toDomain(isBookmarked)`、`BookmarkEntity.toDomain()`、`Article.toBookmarkEntity(savedAt)`、`ForecastResponseDto.toEntity(fetchedAt)`、`WeatherSnapshotEntity.toDomain(today: LocalDate)`、`ProductDto.toEntity(position)`、`ServiceCardEntity.toDomain()`、`Throwable.toAppError()`（`UnknownHostException/ConnectException → OFFLINE`、`SocketTimeoutException → TIMEOUT`、`HttpException → SERVER`、`SerializationException → PARSE`）。

---

## 7. 套件 / 目錄結構與主要 class

根 package：`com.waynejiang.linefeed`（applicationId 相同）。各 module namespace：`com.waynejiang.linefeed.<module>`（例：`...core.data`、`...feature.feed`）。

```
LineFeed/
├── build-logic/                     # included build（convention plugins）
│   ├── settings.gradle.kts          # 讀取 ../gradle/libs.versions.toml
│   └── convention/
│       ├── build.gradle.kts         # `kotlin-dsl`；compileOnly AGP / KGP / KSP / compose-compiler gradle plugin
│       └── src/main/kotlin/
│           ├── AndroidApplicationConventionPlugin.kt   # id: linefeed.android.application
│           ├── AndroidLibraryConventionPlugin.kt       # id: linefeed.android.library
│           ├── AndroidComposeConventionPlugin.kt       # id: linefeed.android.compose
│           ├── AndroidFeatureConventionPlugin.kt       # id: linefeed.android.feature
│           ├── HiltConventionPlugin.kt                 # id: linefeed.hilt
│           ├── JvmLibraryConventionPlugin.kt           # id: linefeed.jvm.library
│           └── ProjectExtensions.kt                    # `val Project.libs`
├── gradle/libs.versions.toml, gradle/wrapper/
├── settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew, gradlew.bat, .gitignore
├── .github/workflows/ci.yml
├── app/
├── core/domain/  core/data/  core/designsystem/  core/testing/
├── feature/feed/  feature/detail/  feature/saved/
└── docs/PLAN.md, README.md, DECISIONS.md, AI_USAGE.md
```

Convention plugins 內容：
- `linefeed.android.application`：apply `com.android.application`；`compileSdk = 37`、`minSdk = 24`、`targetSdk = 36`；Java 17 source/target；`isCoreLibraryDesugaringEnabled = true` + `coreLibraryDesugaring(libs.desugar.jdk.libs)`。
- `linefeed.android.library`：apply `com.android.library`；同上 SDK/Java/desugaring；`testOptions.unitTests.isIncludeAndroidResources = true`；加 `testImplementation(junit, kotlinx-coroutines-test, turbine)`。
- `linefeed.android.compose`：apply `org.jetbrains.kotlin.plugin.compose`；`buildFeatures.compose = true`；加 `platform(compose-bom)`、ui、ui-tooling-preview、material3、`debugImplementation(ui-tooling)`。
- `linefeed.android.feature`：apply library + compose + hilt + `org.jetbrains.kotlin.plugin.serialization`；加 `project(":core:domain")`、`project(":core:designsystem")`、lifecycle-runtime-compose、lifecycle-viewmodel-compose、navigation-compose、hilt-lifecycle-viewmodel-compose、kotlinx-serialization-json、`testImplementation(project(":core:testing"))`。
- `linefeed.hilt`：apply `com.google.devtools.ksp` + `com.google.dagger.hilt.android`；加 `hilt-android`、`ksp(hilt-compiler)`。
- `linefeed.jvm.library`：apply `org.jetbrains.kotlin.jvm`；Java 17 target（`java { sourceCompatibility/targetCompatibility = 17 }` + `kotlin { compilerOptions { jvmTarget = JVM_17 } }`，**不要用 `jvmToolchain(17)`**——本機只有 JDK 21，toolchain 會嘗試下載 JDK 17）。
- **AGP 9 內建 Kotlin：不要 apply `org.jetbrains.kotlin.android`**。為避開 AGP 9 `CommonExtension` 泛型變動，application 與 library plugin 各自 `extensions.configure<ApplicationExtension>` / `configure<LibraryExtension>`，共用邏輯抽成接受 lambda 的小函式即可。
- 若 convention plugins 與 AGP 9 API 纏鬥超過 30 分鐘：退回在各 module 直接寫設定（重複但可接受），並在 NOTES 記錄。

### 7.1 `core:domain`（純 JVM；依賴：kotlinx-coroutines-core）

```
com.waynejiang.linefeed.core.domain
├── model/        Article, SavedArticle, Weather, CurrentWeather, DailyForecast, WeatherCondition, ServiceCard,
│                 NetworkStatus, ContentSource, AppError
├── time/         interface AppClock { fun now(): Instant }
├── network/      interface NetworkMonitor { val status: Flow<NetworkStatus> }
├── freshness/    TtlConfig, RefreshTrigger, RefreshDecision, SkipReason, FreshnessPolicy
├── refresh/      SourceResult, RefreshReport, RefreshStatus, LoadMoreResult, interface FeedRefresher
├── repository/   ArticleRepository, BookmarkRepository, WeatherRepository, ServiceCardRepository
└── util/         suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T>   // 重新拋出 CancellationException
                  class SingleFlight<K : Any, V>(scope: CoroutineScope) { suspend fun run(key: K, block: suspend () -> V): V }
```

關鍵簽章：
```kotlin
enum class RefreshTrigger { COLD_START, FOREGROUND, NETWORK_RESTORED, USER_PULL }
data class SourceTtl(val unmetered: Duration, val metered: Duration, val outdatedAfter: Duration?)
data class TtlConfig(val bySource: Map<ContentSource, SourceTtl>) { companion object { val Default: TtlConfig } }
enum class SkipReason { FRESH, OFFLINE }
sealed interface RefreshDecision { data object Fetch; data class Skip(val reason: SkipReason) }

class FreshnessPolicy(private val clock: AppClock, private val config: TtlConfig = TtlConfig.Default) {
    fun evaluate(source: ContentSource, lastSuccessAt: Instant?, network: NetworkStatus, trigger: RefreshTrigger): RefreshDecision
    fun isOutdated(source: ContentSource, lastSuccessAt: Instant?): Boolean
}

sealed interface SourceResult { data object Success; data class Skipped(val reason: SkipReason); data class Failed(val error: AppError) }
data class RefreshReport(val results: Map<ContentSource, SourceResult>)
data class RefreshStatus(
    val inFlight: Set<ContentSource> = emptySet(),
    val userInitiated: Boolean = false,                     // 只有 USER_PULL 才讓 UI 顯示下拉指示器
    val lastResults: Map<ContentSource, SourceResult> = emptyMap(),
    val lastSuccessAt: Map<ContentSource, Instant> = emptyMap(),
)
interface FeedRefresher {
    val status: StateFlow<RefreshStatus>
    suspend fun refresh(trigger: RefreshTrigger): RefreshReport
}
sealed interface LoadMoreResult { data class Loaded(val newItems: Int); data object EndReached; data class Failed(val error: AppError) }

interface ArticleRepository {
    fun observeFeed(): Flow<List<Article>>              // 已合併 isBookmarked
    fun observeArticle(id: Long): Flow<Article?>        // 收藏快照優先，其次 feed 快取
    suspend fun loadNextPage(): LoadMoreResult          // 內部 single-flight；快取為空時直接回 Loaded(0)、不打網路（第一頁只由 FeedRefresher 負責；VM 也不會在空列表時呼叫）
}
interface BookmarkRepository {
    fun observeSaved(query: String = ""): Flow<List<SavedArticle>>
    fun observeBookmarkedIds(): Flow<Set<Long>>
    suspend fun setBookmarked(article: Article, bookmarked: Boolean)
    suspend fun retryPendingImageDownloads()
}
interface WeatherRepository { fun observeWeather(): Flow<Weather?> }
interface ServiceCardRepository { fun observeServiceCards(): Flow<List<ServiceCard>> }
```
（各來源的 `refresh()` 不放在 domain interface：刷新只能經由 `FeedRefresher`，由它套用新鮮度政策。data 層內部以 `internal interface SourceRefresher { val source: ContentSource; suspend fun refresh(): SourceResult }` 實作。）

另：`fun refreshTriggers(isForeground: Flow<Boolean>, network: Flow<NetworkStatus>): Flow<RefreshTrigger>` 放在 `refresh/RefreshTriggers.kt`（純 Flow 邏輯）：
- 第一次 `isForeground == true` → `COLD_START`；之後每次 false→true → `FOREGROUND`
- 前景中 `OFFLINE → METERED/UNMETERED` → `NETWORK_RESTORED`
- 背景中的網路變化不發射；METERED ↔ UNMETERED 不發射

### 7.2 `core:data`（Android library + hilt + serialization + Room）

```
com.waynejiang.linefeed.core.data
├── network/
│   ├── SpaceflightApi, OpenMeteoApi, DummyJsonApi                 # Retrofit interfaces
│   ├── dto/ ArticleDto…, ForecastResponseDto…, ProductDto…
│   ├── ArticleRemoteDataSource (internal interface) + RetrofitArticleRemoteDataSource
│   │     suspend fun fetchPage(limit: Int, publishedAtLte: Instant?): List<ArticleDto>
│   ├── WeatherRemoteDataSource + Retrofit 實作   suspend fun fetchForecast(): ForecastResponseDto
│   ├── ServiceRemoteDataSource + Retrofit 實作   suspend fun fetchProducts(limit: Int): List<ProductDto>
│   └── ImageDownloader (internal interface) + OkHttpImageDownloader
├── database/
│   ├── LineFeedDatabase, entity/…, dao/…, Converters（僅 dailyJson）
├── mapper/ ArticleMappers.kt, WeatherMappers.kt, ServiceCardMappers.kt, ErrorMappers.kt
├── repository/
│   ├── OfflineFirstArticleRepository : ArticleRepository, SourceRefresher(ARTICLES)
│   │     refresh(): 抓第一頁 → transaction{ 有重疊? upsert : deleteAll+insert; trimTo(300); markSuccess }
│   │     loadNextPage(): cursor = oldestPublishedAt → fetch → 新 id 數 → upsert → Loaded/EndReached
│   ├── OfflineFirstWeatherRepository : WeatherRepository, SourceRefresher(WEATHER)
│   ├── OfflineFirstServiceCardRepository : ServiceCardRepository, SourceRefresher(SERVICES)
│   └── DefaultBookmarkRepository : BookmarkRepository
├── refresh/
│   └── DefaultFeedRefresher : FeedRefresher
│         依賴 FreshnessPolicy, NetworkMonitor, SyncMetadataDao, Set<SourceRefresher>, SingleFlight, AppClock
│         refresh(trigger) = coroutineScope { 逐來源 async { evaluate → Skip 或 singleFlight.run(source){ refresher.refresh() } } }.awaitAll()
│         一個來源失敗不影響其他（各自 suspendRunCatching）；更新 status（inFlight / userInitiated / lastResults）
├── network/ConnectivityNetworkMonitor : NetworkMonitor   # callbackFlow + registerDefaultNetworkCallback；
│         NET_CAPABILITY_INTERNET && VALIDATED → online；NOT_METERED → UNMETERED；distinctUntilChanged；
│         conflate；初始值取 activeNetwork 的 capabilities
├── time/SystemAppClock : AppClock
└── di/
    ├── NetworkModule   (Json, OkHttpClient+Cache, 3×Retrofit, 3×Api)
    ├── DatabaseModule  (Room.databaseBuilder, DAOs)
    ├── DataModule      (@Binds 各 repository/NetworkMonitor/AppClock/FeedRefresher；@IntoSet SourceRefresher；@Provides FreshnessPolicy)
    └── CoroutinesModule(@IoDispatcher, @ApplicationScope)
```

### 7.3 `core:designsystem`
```
theme/ Color.kt, Theme.kt (LineFeedTheme(darkTheme = isSystemInDarkTheme())), Type.kt
component/ FullScreenMessage, OfflineBanner, BookmarkIconButton, FeedImage, SourceChip, SkeletonCard
format/ object RelativeTimeFormatter { fun format(instant: Instant, now: Instant, zone: ZoneId): String }   // now 由呼叫端（VM 用 AppClock）傳入，保持純函式
        // "Just now" / "5m ago" / "2h ago" / "3d ago" / 超過 7 天或未來 → "Sep 18"
format/ WeatherConditionUi: WeatherCondition → (ImageVector, @StringRes label)
```
（`RelativeTimeFormatter` 需要 `java.time`，本 module 依賴 `core:domain`。）

### 7.4 `core:testing`（純 JVM；`api` 依賴 junit、kotlinx-coroutines-test、turbine、core:domain）
```
FakeClock(initial: Instant) : AppClock { fun advanceBy(d: Duration); fun set(i: Instant) }
FakeNetworkMonitor(initial = UNMETERED) : NetworkMonitor { fun set(status) }
FakeArticleRepository : ArticleRepository     # MutableStateFlow<List<Article>>；nextPageResults: ArrayDeque<LoadMoreResult>；
                                              # 可選 gate: CompletableDeferred<Unit> 控制 loadNextPage 何時完成；記錄呼叫次數
FakeBookmarkRepository, FakeWeatherRepository, FakeServiceCardRepository
FakeFeedRefresher : FeedRefresher             # 記錄 triggers；可設定回傳的 RefreshReport；可 gate
MainDispatcherRule(testDispatcher = UnconfinedTestDispatcher()) : TestWatcher
TestData: fun article(id: Long, publishedAt: Instant = ..., …): Article；weather(...)；serviceCard(...)
```

### 7.5 `feature:feed`
```
FeedRoute (@Serializable data object), NavGraphBuilder.feedScreen(onArticleClick: (Long) -> Unit, onOpenSaved: () -> Unit)
FeedViewModel(@HiltViewModel; ArticleRepository, WeatherRepository, ServiceCardRepository, BookmarkRepository,
              FeedRefresher, NetworkMonitor, FreshnessPolicy? → 用於 isOutdated；AppClock)
    val uiState: StateFlow<FeedUiState>
    fun onPullToRefresh(); fun onRetry(); fun onNearEnd(); fun onRetryAppend()
    fun onToggleBookmark(article: Article); fun onUserMessageShown(id: Long)
FeedUiState.kt, FeedItem.kt, FullScreenState.kt, AppendState.kt, deriveFullScreenState()
FeedAssembler (object 或 class): fun assemble(weather: WeatherCardState?, articles: List<Article>, services: List<ServiceCard>,
                                             firstServiceAfter: Int = 3, serviceEvery: Int = 6): List<FeedItem>
FeedScreen.kt (stateful: hiltViewModel + collectAsStateWithLifecycle) / FeedContent (stateless, 可 Preview)
cells/ WeatherHeroCard, TopStoryCard, ArticleRowCell, ServiceCardCell, AppendFooter
```
FeedAssembler 規則：`[WeatherHero]` + 第一篇 `TopStory` + 其餘 `ArticleRow`；第 3 篇文章之後插第一張服務卡，之後每 6 篇插一張，服務卡循環使用；沒有服務卡就不插；沒有文章時回傳空列表（由 fullScreen 狀態接手）。

### 7.6 `feature:detail`
```
ArticleDetailRoute(@Serializable data class(articleId: Long)), NavGraphBuilder.articleDetailScreen(onBack)
ArticleDetailViewModel(SavedStateHandle, ArticleRepository, BookmarkRepository, NetworkMonitor)
    val uiState: StateFlow<DetailUiState>; fun onToggleBookmark()
ArticleDetailScreen / ArticleDetailContent
```
收藏圖片：詳情頁需要 `localImagePath` → `ArticleRepository.observeArticle` 回傳的 `Article` 不含它；以 `BookmarkRepository.observeSaved()` 或新增 `observeSavedArticle(id): Flow<SavedArticle?>` 取得（實作時二選一，建議在 BookmarkRepository 加 `observeSavedArticle(id)`，VM 用 `combine`）。

### 7.7 `feature:saved`
```
SavedRoute, NavGraphBuilder.savedScreen(onArticleClick)
SavedViewModel(BookmarkRepository, NetworkMonitor)
    val uiState: StateFlow<SavedUiState>; fun onQueryChange(q: String)（debounce 300ms）; fun onRemoveBookmark(article: Article)
SavedScreen / SavedContent / SavedArticleRow
```

### 7.8 `app`
```
LineFeedApplication (@HiltAndroidApp, SingletonImageLoader.Factory) → newImageLoader(context) 使用注入的 OkHttpClient
AppRefreshInitializer (@Inject; @ApplicationScope, FeedRefresher, BookmarkRepository, NetworkMonitor)
    fun start(isForeground: Flow<Boolean>)  // collect refreshTriggers(...) → refresher.refresh(t)；NETWORK_RESTORED/FOREGROUND 時也 retryPendingImageDownloads()
    // isForeground 由 ProcessLifecycleOwner.lifecycle.currentStateFlow.map { it.isAtLeast(STARTED) } 提供
MainActivity (@AndroidEntryPoint; enableEdgeToEdge(); setContent { LineFeedTheme { LineFeedApp() } })
LineFeedApp: Scaffold + NavigationBar(Reading / Saved) + NavHost(startDestination = FeedRoute)
AndroidManifest: INTERNET, ACCESS_NETWORK_STATE
```

---

## 8. 版本清單

### 8.1 驗證證據（為什麼選這組）
- 本機 `~/.gradle` 快取中，這組版本在 2026-09-18 曾於 multi-module Compose 專案**成功 build 與跑測試**（Gradle 9.7.1 daemon log 有多次 `BUILD SUCCESSFUL`），另一個專案 `~/Github/MyPoke` 以 AGP 9.4.0 + Gradle 9.7.1 + KSP 2.3.11 產出 APK。
- **Compose UI 1.12.1（BOM 2026.09.00）的 AAR metadata 要求 `minCompileSdk=37`、`minAndroidGradlePluginVersion=9.1.0`**；`core-ktx 1.19.0` 同樣要求 compileSdk 37 → **compileSdk 必須是 37、AGP 必須 ≥ 9.1**。
- 所有 AAR 的 minSdk ≤ 24（Compose/Room/Coil/core 為 23、navigation-compose 為 24）→ minSdk 24 可行。
- 本機 SDK：platforms android-35/36/37.0、build-tools 35/36/37 → compileSdk 37 可用。
- Robolectric 4.17 最高支援 SDK 36，且本機 `~/.m2` 已有 `android-all-instrumented:16-robolectric-...`（API 36）→ **targetSdk 36**、Robolectric 測試標 `@Config(sdk = [36])`。

### 8.2 `gradle/libs.versions.toml`

```toml
[versions]
agp = "9.4.0"
kotlin = "2.4.20"
ksp = "2.3.12"
composeBom = "2026.09.00"          # ui 1.12.1, material3 1.4.0, material-icons-extended 1.7.8
activityCompose = "1.13.0"
coreKtx = "1.19.0"
lifecycle = "2.11.0"
navigationCompose = "2.10.1"
hilt = "2.60.1"
androidxHilt = "1.4.0"
room = "2.8.5"
retrofit = "3.0.0"
okhttp = "5.5.0"
kotlinxSerialization = "1.11.0"
coroutines = "1.11.0"
coil = "3.6.2"
desugarJdkLibs = "2.1.5"
junit = "4.13.2"
turbine = "1.2.1"
robolectric = "4.17"
androidxTestCore = "1.7.0"
androidxTestExtJunit = "1.3.0"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
androidx-hilt-lifecycle-viewmodel-compose = { module = "androidx.hilt:hilt-lifecycle-viewmodel-compose", version.ref = "androidxHilt" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { module = "com.squareup.retrofit2:converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver3", version.ref = "okhttp" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }
desugar-jdk-libs = { module = "com.android.tools:desugar_jdk_libs", version.ref = "desugarJdkLibs" }
junit = { module = "junit:junit", version.ref = "junit" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExtJunit" }
# build-logic 用
android-gradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }
kotlin-gradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
ksp-gradlePlugin = { module = "com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin", version.ref = "ksp" }
compose-gradlePlugin = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
room = { id = "androidx.room", version.ref = "room" }
```

**不使用**：MockK（見 §9.1）、Paging 3、DataStore、WorkManager、Compose UI test（延後）。
`androidx-test-ext-junit` 僅在需要 `AndroidJUnit4` runner 的 Robolectric 測試使用（也可直接用 `RobolectricTestRunner`，二選一即可，擇一後移除另一個）。

### 8.3 Gradle wrapper（本機沒有系統 `gradle`）
本機已有 Gradle 9.7.1 發行版快取，用它產生 wrapper：
```bash
cd /Users/wayne/Github/LineFeed
# 先建立 settings.gradle.kts（至少 rootProject.name = "LineFeed"），再執行：
GRADLE_BIN=$(ls -d ~/.gradle/wrapper/dists/gradle-9.7.1-bin/*/gradle-9.7.1/bin/gradle | head -1)
"$GRADLE_BIN" wrapper --gradle-version 9.7.1 --distribution-type bin
```
- 備案 A：從 `~/Github/MyPoke` 複製 `gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar`，自行寫 `gradle-wrapper.properties`（`distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip`）。
- 備案 B：下載 `https://services.gradle.org/distributions/gradle-9.7.1-bin.zip` 解壓後執行同樣指令。
- `gradlew` 必須 `chmod +x` 並以可執行權限 commit（`git update-index --chmod=+x gradlew`）。
- `local.properties`（`sdk.dir=/Users/wayne/Library/Android/sdk`）只在本機建立，**必須在 `.gitignore`**。

### 8.4 `gradle.properties`
```
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
kotlin.code.style=official
```
若 configuration cache 與某 plugin 衝突，改為 `false` 並在 NOTES 記錄原因。

### 8.5 Root 聚合測試 task
Android module 的單元測試 task 是 `testDebugUnitTest`，JVM module 是 `test`；為了一行跑完，在 root `build.gradle.kts` 註冊 `unitTest` task，以**字串路徑**明確 `dependsOn`：`:core:domain:test`、`:core:testing:test`、`:core:data:testDebugUnitTest`、`:core:designsystem:testDebugUnitTest`、`:feature:feed:testDebugUnitTest`、`:feature:detail:testDebugUnitTest`、`:feature:saved:testDebugUnitTest`、`:app:testDebugUnitTest`（用字串路徑不做跨專案 configuration，對 configuration cache 安全）。每新增 module 要更新這個列表。

---

## 9. 測試策略

### 9.1 原則
- **Fake 優於 mock**：fake 是真的 interface 實作，有狀態、可重用（放 `core:testing`），測試斷言「結果狀態」而不是「呼叫了哪個方法」，重構內部實作時測試不會壞；Kotlin `final` class、suspend function、Flow 用 mock 很囉唆且脆弱。只有在需要「驗證呼叫次數」時，在 fake 裡加 counter。
- **Repository 用真的 Room（in-memory，Robolectric）+ fake remote**：cache/網路協調的 bug 大多在 SQL 與 transaction，fake DAO 測不出來。
- **網路層用 MockWebServer + 真實 JSON fixture**（從 §6 的 curl 結果存成 `core/data/src/test/resources/fixtures/*.json`），驗證 query 參數與解析；**測試永遠不打真實 API**。
- 所有時間用 `FakeClock`；所有協程用 `runTest` + `StandardTestDispatcher`/`UnconfinedTestDispatcher`；Flow 用 Turbine。
- Room 測試：`Room.inMemoryDatabaseBuilder(context, LineFeedDatabase::class.java).allowMainThreadQueries().setQueryCoroutineContext(testDispatcher)`；`@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [36])`。
- **JUnit4 only，不要呼叫 `useJUnitPlatform()`**；有 `src/test` 的 module 一定要至少有一個 `@Test`（Gradle 9 對「有測試原始碼但沒發現測試」會直接 fail，本機先前就踩過）。

### 9.2 測試類別與情境

**`core:domain`**
- `FreshnessPolicyTest`
  - 從未成功 → Fetch
  - 年齡 < TTL（metered）→ Skip(FRESH)
  - 年齡 == TTL → Fetch（邊界）
  - 相同年齡 30 分鐘的文章：metered（60 分）Skip、unmetered（20 分）Fetch
  - OFFLINE → Skip(OFFLINE)，即使從未成功、即使 USER_PULL
  - USER_PULL 在資料新鮮時仍 Fetch
  - lastSuccessAt 在未來（時鐘被調）→ Fetch
  - 各來源 TTL 獨立：同一時間點天氣過期、服務卡仍新鮮
  - `isOutdated`：天氣 > 3h true、服務卡（無門檻）永遠 false、null → true
- `WeatherConditionTest`：每個 WMO 區間代表值 + 未知碼 → UNKNOWN
- `SingleFlightTest`
  - 兩個並行呼叫同 key → block 只執行一次，兩者拿到同值
  - 不同 key 互不影響、可並行
  - 完成後再呼叫 → 重新執行
  - block 拋例外 → 所有等待者收到例外，下一次呼叫可重試（不被毒化）
  - 其中一個呼叫者被取消 → 共享工作不被取消，另一個呼叫者仍拿到結果
- `SuspendRunCatchingTest`：一般例外 → failure；`CancellationException` 被重新拋出
- `RefreshTriggersTest`（Turbine）
  - 首次前景 → COLD_START；背景再回前景 → FOREGROUND
  - 前景中 OFFLINE→UNMETERED → NETWORK_RESTORED
  - 背景中網路恢復 → 不發射；之後回前景 → FOREGROUND（只一次）
  - METERED↔UNMETERED → 不發射
  - 重複的 foreground=true → 不重複發射

**`core:data`**
- `SpaceflightApiTest`（MockWebServer）：解析 fixture（含微秒 updated_at、null image_url、未知欄位）；第一頁不帶 `published_at_lte`；下一頁帶正確 ISO 字串；`ordering=-published_at`、`limit=20`
- `OpenMeteoApiTest`：解析 fixture；request 帶 `timezone=Asia/Taipei` 等參數
- `DummyJsonApiTest`：解析；帶 `select=`；缺 `brand` 不爆
- `ArticleMappersTest`：ISO 解析、空字串 image → null、authors 串接與還原、entity↔domain 往返
- `WeatherMappersTest`：平行陣列 zip；長度不一致時取最短並不崩潰；過濾今天之前的日期；本地時間無時區解析
- `ServiceCardMappersTest`：ctaLabel 四捨五入、discount < 1 → "Open"、actionUrl 格式
- `ErrorMappersTest`：各例外 → AppError
- `FeedArticleDaoTest`（Robolectric）：排序 publishedAt DESC, id DESC（同秒以 id 排）；`oldestPublishedAt`；`trimTo` 保留最新 N 筆；`existingIds`
- `BookmarkDaoTest`：搜尋大小寫不敏感、title 與 newsSite 皆可命中、`%`/`_` 被 escape；`pendingImageDownloads`
- `OfflineFirstArticleRepositoryTest`（Robolectric + in-memory Room + `FakeArticleRemoteDataSource` + FakeClock）
  - 空快取 refresh → 寫入第一頁、`sync_metadata.lastSuccessAt = now`
  - 有重疊的 refresh → 新文章加在前面、舊頁保留
  - 無重疊（缺口）refresh → 快取被替換，只剩新一頁
  - refresh 失敗 → 快取不變、lastSuccessAt 不變、lastError 被記錄、回傳 Failed(OFFLINE)
  - 超過 300 筆時修剪最舊的
  - `loadNextPage` 用最舊 publishedAt 當游標（斷言 fake 收到的參數）
  - `loadNextPage` 邊界重複 id 被去重，回傳新增數量正確
  - 回傳 < pageSize → EndReached；整頁都是已存在 id → EndReached（無限迴圈保護）
  - 兩個並行 `loadNextPage` → remote 只被呼叫一次
  - `observeFeed` 反映收藏狀態，收藏/取消後即時更新
  - `observeArticle`：feed 快取被清空後仍能從收藏快照取得；兩邊都沒有 → null
- `OfflineFirstWeatherRepositoryTest`：refresh 寫入 snapshot 與 metadata；失敗保留舊資料；observe 過濾過期日期（FakeClock 前進一天）
- `OfflineFirstServiceCardRepositoryTest`：refresh 整批替換且保留順序；失敗保留舊資料
- `DefaultBookmarkRepositoryTest`（Robolectric + `FakeImageDownloader` + `TemporaryFolder`）
  - 收藏 → 寫入快照、下載圖片、localImagePath 更新
  - 下載失敗 → localImagePath 為 null；`retryPendingImageDownloads` 成功後補上
  - 取消收藏 → row 與檔案都刪除
  - 收藏在 feed 快取清空後仍存在
  - 無圖片 URL 的文章 → 不嘗試下載
- `DefaultFeedRefresherTest`（fake SourceRefresher ×3 + in-memory 或 fake metadata + FakeClock + FakeNetworkMonitor）
  - COLD_START：只刷新過期的來源（例如天氣過期、文章新鮮 → 只打天氣）
  - OFFLINE：不打任何來源，report 全為 Skipped(OFFLINE)
  - USER_PULL：三個來源都打，`status.userInitiated == true` 期間為真、結束後 false（Turbine）
  - 一個來源失敗，其他仍成功；report 反映各自結果
  - 同時兩次 refresh → 每個來源只被呼叫一次（single-flight）
  - `status.inFlight` 在執行中包含該來源、完成後清空（用 gate 控制 fake 完成時機）
  - metered vs unmetered：同一年齡在 unmetered 會刷新、metered 不會

**`core:designsystem`**
- `RelativeTimeFormatterTest`：<1 分 "Just now"、59 分、1 小時邊界、23 小時、1 天、6 天、7 天以上 → 絕對日期、**未來時間 → 絕對日期**

**`feature:feed`**
- `FeedAssemblerTest`：天氣永遠第一；第一篇是 TopStory；服務卡位置（第 3 篇後、之後每 6 篇）；服務卡不足時循環且 key 唯一；無服務卡不插入；無文章 → 空列表；所有 key 唯一
- `DeriveFullScreenStateTest`：§5.2 規則表每一列
- `FeedViewModelTest`（全部 fake + MainDispatcherRule）
  - 無快取 + 刷新中 → fullScreen = Loading
  - 無快取 + OFFLINE → Offline；網路恢復且文章出現 → 列表
  - 無快取 + 刷新失敗 → Error；`onRetry()` → refresher 收到 USER_PULL
  - 有快取 + 背景刷新中 → 顯示列表、`isUserRefreshing == false`
  - `onPullToRefresh()` → USER_PULL、`isUserRefreshing` true→false
  - 有快取 + 刷新失敗 → userMessage 出現；`onUserMessageShown` 清除
  - 有快取 + OFFLINE → `isOffline == true`、列表仍在
  - `onNearEnd()` → loadNextPage 被呼叫一次；Loading 期間再呼叫被忽略（用 gate）
  - loadNextPage Failed → appendState Error；`onRetryAppend()` 重試
  - EndReached 之後 `onNearEnd()` 不再呼叫 repository；USER_PULL 刷新成功後重置為 Idle
  - OFFLINE 時 `onNearEnd()` → appendState Offline，不呼叫 repository
  - `onToggleBookmark` → BookmarkRepository 被更新，列表中 isBookmarked 反映
  - 天氣：無資料+天氣 in-flight → WeatherCardState.Loading；失敗 → Unavailable；lastSuccessAt 過舊 → isOutdated

**`feature:detail`**
- `ArticleDetailViewModelTest`：以 `SavedStateHandle(mapOf("articleId" to 1L))` 載入 → Content；repository 回 null → NotFound；toggle 收藏；OFFLINE → `isOffline == true`；收藏後 localImagePath 優先

**`feature:saved`**
- `SavedViewModelTest`：無收藏 → items 空、isLoading false；有收藏 → 依 savedAt 排序；`onQueryChange` 經 300ms debounce 後才過濾（`advanceTimeBy(299)` 不變、`advanceTimeBy(1)` 變）；`onRemoveBookmark`；OFFLINE banner 旗標

---

## 10. 實作順序與 Commit 計畫

**順序的理由（README Plan & Sequencing ② 素材）**：
1. **先把 build + CI 打穩**：AGP 9 / Kotlin 2.4 / KSP 是最大的未知數，先付掉；CI 越早加，之後每個 commit 都被驗證。
2. **由內而外（domain → data → UI）**：新鮮度與快取是評分核心且最需要測試，先在純 JVM 上把政策寫對、測完，再接網路與 DB；UI 最後，因為它只是 state 的渲染。
3. **先 must-have 再 nice-to-have**：搜尋放最後，時間不夠第一個砍。
4. 每個 commit 都可 build、測試全綠，沒有「半成品 commit」。

每步驗收：除非另註，`./gradlew assembleDebug unitTest` 必須通過。每步結束前也要跑一次 `git status` 確認沒有 `build/`、`local.properties`、`.idea/` 被加入。

| # | Commit message | 內容 | 該步新增的測試 | 驗收 |
|---|---|---|---|---|
| 0 | `docs: add architecture and implementation plan` | 本文件（使用者提交） | — | — |
| 1 | `build: bootstrap gradle wrapper, version catalog and convention plugins` | wrapper（9.7.1）、`.gitignore`、`settings.gradle.kts`（include 全部 8 module + `includeBuild("build-logic")`）、root `build.gradle.kts`（plugins apply false + `unitTest` task）、`gradle.properties`、`libs.versions.toml`、`build-logic` 6 個 convention plugins；每個 module 最小骨架（manifest/namespace、空 package）；`app` 有 `@HiltAndroidApp` Application + 顯示 "LineFeed" 的 Compose `MainActivity` | 無（**不要建立空的 src/test**） | `./gradlew assembleDebug`；`./gradlew unitTest`（全部 NO-SOURCE 也算過） |
| 2 | `ci: add github actions workflow for build and unit tests` | `.github/workflows/ci.yml`：push + pull_request；`ubuntu-latest`；`actions/checkout@v4`、`actions/setup-java@v4`（temurin 21）、`android-actions/setup-android@v3`、`gradle/actions/setup-gradle@v4`；執行 `./gradlew assembleDebug unitTest --stacktrace`；失敗時上傳 `**/build/reports/tests/` artifact | 無 | yml 語法正確；本機指令同上。push 後由使用者確認 Actions 綠燈 |
| 3 | `feat(domain): add domain models, freshness policy and refresh triggers` | `core:domain` 全部（§7.1）：model、AppClock、NetworkMonitor、TtlConfig、FreshnessPolicy、RefreshTrigger/Decision、SourceResult/RefreshReport/RefreshStatus、repository interfaces、FeedRefresher、`refreshTriggers()`、`SingleFlight`、`suspendRunCatching`；`core:testing`：FakeClock、FakeNetworkMonitor、MainDispatcherRule、TestData | FreshnessPolicyTest、WeatherConditionTest、SingleFlightTest、SuspendRunCatchingTest、RefreshTriggersTest | `./gradlew :core:domain:test` + 全域驗收 |
| 4 | `feat(data): add retrofit clients and dtos for spaceflight, open-meteo and dummyjson` | `core:data` network 套件：3 個 Api、DTO、RemoteDataSource interfaces + Retrofit 實作、`NetworkModule`（Json、OkHttp+Cache、3×Retrofit）、`ErrorMappers`；fixtures（由 §6 curl 取得並存檔） | SpaceflightApiTest、OpenMeteoApiTest、DummyJsonApiTest、ErrorMappersTest | `./gradlew :core:data:testDebugUnitTest` |
| 5 | `feat(data): add room database for feed cache, bookmarks and sync metadata` | entities、DAOs、`LineFeedDatabase`、`DatabaseModule`、room gradle plugin schema 匯出（commit `core/data/schemas/.../1.json`）、DTO→Entity→Domain mappers | FeedArticleDaoTest、BookmarkDaoTest、ArticleMappersTest、WeatherMappersTest、ServiceCardMappersTest | 同上 |
| 6 | `feat(data): implement offline-first repositories with keyset pagination` | `OfflineFirstArticleRepository`（refresh 重疊合併/替換、trim、loadNextPage keyset + 去重 + single-flight）、`OfflineFirstWeatherRepository`、`OfflineFirstServiceCardRepository`、`DefaultBookmarkRepository`（先不含圖片下載）、`SystemAppClock`、`DataModule` 部分綁定 | OfflineFirstArticleRepositoryTest、OfflineFirstWeatherRepositoryTest、OfflineFirstServiceCardRepositoryTest、DefaultBookmarkRepositoryTest（快照部分） | 同上 |
| 7 | `feat(data): add freshness-aware refresh coordinator and network monitor` | `DefaultFeedRefresher`（policy + single-flight + status）、`ConnectivityNetworkMonitor`、`CoroutinesModule`、`DataModule` 完成（`@IntoSet SourceRefresher`、`@Provides FreshnessPolicy`）；`app`：`AppRefreshInitializer` + `ProcessLifecycleOwner` 接線、Manifest 權限 | DefaultFeedRefresherTest | 全域驗收 + 安裝到模擬器看 Logcat 有刷新紀錄（可選） |
| 8 | `feat(designsystem): add material 3 theme with dark mode and shared state components` | `LineFeedTheme` light/dark、Typography、FullScreenMessage、OfflineBanner、BookmarkIconButton、FeedImage、SourceChip、SkeletonCard、`RelativeTimeFormatter`、`WeatherConditionUi`；每個元件附 `@Preview`（light + dark） | RelativeTimeFormatterTest | 全域驗收 |
| 9 | `feat(feed): add heterogeneous feed with weather hero, service cards and pagination` | `feature:feed` 全部（§7.5）；`app` 加 Coil `SingletonImageLoader.Factory`、NavHost（先只有 Feed）、底部導覽骨架 | FeedAssemblerTest、DeriveFullScreenStateTest、FeedViewModelTest | 全域驗收 + 模擬器手動：冷啟動、下拉、捲到底、飛航模式 |
| 10 | `feat(detail): add article detail screen with bookmark toggle` | `feature:detail`；Feed → Detail 導覽；`BookmarkRepository.observeSavedArticle(id)` | ArticleDetailViewModelTest | 同上 + 手動：收藏後飛航模式開詳情 |
| 11 | `feat(saved): add saved articles screen with offline banner` | `feature:saved`（先不含搜尋）；底部導覽 Reading/Saved 完成；Saved → Detail | SavedViewModelTest（不含 query） | 同上 |
| 12 | `feat(data): persist bookmark images for offline reading` | `ImageDownloader` + `OkHttpImageDownloader`（tmp→rename）、收藏時於 ApplicationScope 下載、取消時刪檔、`retryPendingImageDownloads` 接到 `AppRefreshInitializer`；UI 以 localImagePath 優先 | DefaultBookmarkRepositoryTest 擴充（下載成功/失敗/重試/刪檔） | 同上 + 手動：收藏 → 清除 App cache（設定 → 儲存空間 → 清除快取）→ 飛航模式 → 圖片仍在 |
| 13 | `feat(saved): add offline search over saved articles` | Saved 頁 top bar 搜尋欄、`onQueryChange` debounce、無結果 Empty 變體；小動畫（`animateItem`、收藏 icon crossfade） | SavedViewModelTest 擴充（debounce/過濾） | 全域驗收 |
| 14 | `docs: add readme, decisions log and ai usage notes` | `README.md`（一行執行指令、總覽、截圖可選、**新鮮度策略**（§3 全文精簡版 + 數據表）、**Plan & Sequencing**（§1、§10 的理由）、AI 流程圖（mermaid：Wayne 定約束 → Opus 規劃 PLAN.md → Sonnet 逐步實作 + 測試 → Wayne 審查/修正/提交）、已知限制、若有更多時間）、`DECISIONS.md`（§2 全部，格式：選擇/替代/取捨）、`AI_USAGE.md` 草稿（見下） | — | 文件中所有指令實際可執行 |

**AI_USAGE.md 注意**：必須誠實，**由 Wayne 最後審閱改寫**。Sonnet 只能草擬「確實發生過」的事，可用素材：
- 流程：Opus 做規劃（本文件）、Sonnet 實作與寫測試、Wayne 審查每個 commit。
- 「AI 弄錯被抓到」的真實案例：先前由 Haiku 整理的需求版本出現幻覺（OpenWeather、`ASSESSMENT.md`、「禁止 AI 生成架構」等原文沒有的內容），由人工對照 PDF 原文發現並重寫（見 `requirements.md` 開頭註記）。
- 實作過程中實際遇到的錯誤（例如版本相容、測試失敗）請在 `docs/NOTES.md` 隨手記錄，最後挑 2–3 個寫入。
- 不得捏造 prompt 或事件。

---

## 11. 風險與注意事項

| 風險 | 對策 |
|---|---|
| **AGP 9 內建 Kotlin** | 不要 apply `org.jetbrains.kotlin.android`（會衝突）。Compose compiler / serialization plugin 仍需 apply，版本 = Kotlin 版本 |
| **Compose 1.12 需要 compileSdk 37、AGP ≥ 9.1** | 已驗證 AAR metadata；compileSdk 固定 37。若 CI runner 沒有 platform 37，AGP 會自動下載（`setup-android` 已接受 licenses）；不行就在 workflow 加 `sdkmanager "platforms;android-37.0"` |
| **KSP 版本** | KSP 2.3.x 起版本號不再綁 Kotlin（不是 `2.4.20-x.y.z` 格式），用 2.3.12 |
| **Hilt 與 AGP 9** | Hilt 2.60.1 已驗證可用；`hiltViewModel()` 來自 `androidx.hilt:hilt-lifecycle-viewmodel-compose`（package `androidx.hilt.lifecycle.viewmodel.compose`），不是舊的 `hilt-navigation-compose` |
| **Robolectric 只到 SDK 36** | targetSdk 36；所有 Robolectric 測試 `@Config(sdk = [36])`；第一次跑需要 `android-all-instrumented`（本機 `~/.m2` 已有；CI 會自動下載） |
| **Gradle 9「有測試原始碼卻沒發現測試」直接 fail** | JUnit4、不呼叫 `useJUnitPlatform()`；不要留只有 helper 沒有 `@Test` 的 `src/test`（fake 放 `core:testing/src/main`） |
| **`jvmToolchain(17)` 觸發下載 JDK** | 不用 toolchain，改設 source/target compatibility 與 `jvmTarget` |
| **minSdk 24 用 `java.time`** | 所有 Android module 開 core library desugaring（convention plugin 統一處理） |
| **Spaceflight：未來時間、同秒多篇、summary 很短** | §6.1 已列對策；有對應測試 |
| **Offset 分頁重複/漏資料** | 改用 keyset（`published_at_lte`）+ id 去重 + 無進度即結束 |
| **API 不穩 / 掛掉** | App 以快取優先，失敗只顯示 snackbar/錯誤頁；測試全部用 fixture 不打網路；README 註明若 API 掛掉，已快取與已收藏內容仍可用。（不做本地 mock flavor——需求允許 mock，但會增加 build variant 複雜度；列為備案：若 Spaceflight 長時間不可用，再加 `assets/` fixture 的 `FakeRemoteDataSource` 綁定） |
| **DummyJSON rate limit（100）** | 24h TTL + 單一請求，實際不會觸發 |
| **`runCatching` 吞掉 `CancellationException`** | 一律用 `suspendRunCatching` |
| **`SavedStateHandle.toRoute()` 在 JVM 測試失敗** | VM 用 `savedStateHandle.get<Long>("articleId")` |
| **Room 測試與 coroutine test dispatcher** | `setQueryCoroutineContext(testDispatcher)` + `allowMainThreadQueries()`；Flow 用 Turbine `awaitItem()`，注意 Room 會先發射初始值 |
| **`combine` 超過 5 個 flow** | 先把相關 flow 分組 combine 成中間 data class，再 combine |
| **Edge-to-edge（targetSdk 35+ 強制）** | `enableEdgeToEdge()` + Scaffold `innerPadding`；列表底部加 navigation bar insets |
| **Configuration cache 與 plugin 不相容** | 發生時關閉並記錄 |
| **`material-icons-extended` 很大** | debug APK 變大可接受（release 由 R8 移除未用 icon）；README 註記 |
| **LazyColumn key 重複會 crash** | 服務卡 key 含 slot；文章 key 用 id；FeedAssemblerTest 斷言 key 唯一 |
| **刷新時列表跳動** | LazyColumn 以 key 維持錨點；文章 refresh 採合併而非替換 |
| **收藏圖片佔空間** | 每張約 100 KB，取消收藏即刪；README 已知限制 |
| **不要冒用品牌** | App 名稱 "LineFeed"、package `com.waynejiang.linefeed`；不使用 LINE 官方 logo 或商標素材，只用綠色系配色 |

---

## 12. 給實作者的工作守則

1. 一次只做一個 commit 範圍；完成後跑驗收指令，全綠才 commit（由使用者決定是否由你 commit）。
2. 遇到版本/相容性問題：先查錯誤訊息 → 調整 → 在 `docs/NOTES.md` 記一行「問題 / 原因 / 解法」（這是 AI_USAGE 與 DECISIONS 的真實素材）。
3. 不擅自加入本文件「不使用」清單中的依賴；若認為必要，先在 NOTES 寫理由。
4. 所有 public API（domain interface、ViewModel 事件方法）加簡短 KDoc，說明「為什麼」而不是「做什麼」。
5. 字串放 `strings.xml`；所有可點擊 icon 有 `contentDescription`。
6. 不要為了讓測試通過而改測試的預期行為；若發現計畫中的規則有誤，更新本文件並在 commit message 說明。
