# 架構決策紀錄

每個決策按固定格式：**選擇 / 考慮過的替代方案 / 取捨**。

---

## 1. UI 框架：Jetpack Compose + Material 3

**選擇**：
- Compose 搭配 Material 3 (`PullToRefreshBox`、`NavigationBar`、`Card`)
- `LazyColumn` + `key()` + `contentType()` 支援異質 cell
- 自訂品牌綠主題，支援 light/dark 模式（不用 dynamic color）
- Kotlin 2.4.20 strong skipping 預設開啟，防止無謂重組

**替代方案**：
- Views + RecyclerView (`ListAdapter` + 多 ViewType)
- Jetpack Compose + 不用 Material 3

**取捨**：
- **優**：異質 cell 在 Compose 裡是 `when (item)`，無需 ViewHolder/DiffUtil；狀態驅動 UI 與 UDF 天然契合；Dark theme 零成本
- **劣**：Compose 效能陷阱（不穩定參數造成無謂重組），但本專案用穩定 key + 穩定參數，已規避
- **折衷**：新專案選 Compose，LINE 主 App 既有 Views 專案可用 `ComposeView` 漸進導入

---

## 2. 狀態管理：UDF + 單一 StateFlow<UiState>

**選擇**：
- 每個 ViewModel 對外只有 `val uiState: StateFlow<XxxUiState>` + 若干 `fun onXxx()` 方法
- `uiState = combine(repository flows, 本地 MutableStateFlow).stateIn(WhileSubscribed(5_000), initial)`
- 一次性訊息（snackbar）放在 state 裡（`userMessage: UserMessage?` + `onUserMessageShown()`），不用 Channel/SharedFlow
- 分頁內容**例外**：Feed 的文章 + 服務卡是 `Flow<PagingData<FeedItem>>`（不進 UiState），因為 `PagingData` 不是可比較的值
- 「整頁狀態」由純函式 `deriveFullScreenState(...)` 推導，單獨測試

**替代方案**：
- MVI 框架（Orbit / Mavericks）
- 多個獨立 `StateFlow`
- `LiveData` 或 Compose `mutableStateOf`

**取捨**：
- **優**：單一 state 物件讓「不可能狀態」難以出現；測試只斷言一個值；config change 不遺失訊息（Channel 會丟）
- **劣**：每次任何欄位變動都複製整個 data class（此規模可忽略）；Feed 畫面有兩個狀態來源（UiState + LoadState）
- **選擇理由**：不引入 MVI 框架以避免額外概念與依賴；Flow 是 Room/Compose/Lifecycle 的原生語言

---

## 3. 相依注入（DI）：Hilt

**選擇**：
- Hilt + KSP（編譯期代碼生成）
- `@HiltViewModel` 自動 ViewModel factory
- `@Binds` 綁定 interface → 實作；qualifier 用 `@IoDispatcher`、`@ApplicationScope`、`@SpaceflightRetrofit` 等
- 單元測試**不使用** Hilt：所有 class 都是 constructor injection，測試直接 `new` 並傳入 fake

**替代方案**：
- Koin（runtime 解析，錯誤在執行期才爆）
- 手動 DI（`AppContainer`，ViewModel factory 樣板多）
- kotlin-inject / Metro（新但生態較小）

**取捨**：
- **優**：編譯期驗證圖；Android 標準，與 Navigation/ViewModel 整合；KSP 比 KAPT 快
- **劣**：KSP 編譯時間與註解樣板；測試不用 Hilt 需要自己管理依賴（但反而更簡單）
- **選擇理由**：編譯期驗證曝露圖的問題，避免執行期驚喜

---

## 4. 持久化：Room（唯一 DB），不用 DataStore

**選擇**：
- Room 2.8.5，一個 `LineFeedDatabase`，5 張表
- 資料寫入與 `sync_metadata.lastSuccessAt` 更新**在同一個 transaction**
- Schema 匯出到 `core/data/schemas/`，commit 進 repo
- **不使用** `fallbackToDestructiveMigration`（收藏是使用者資料）

**替代方案**：
- DataStore（存 timestamp）
- SQLDelight
- 檔案 JSON 快取
- 只靠 OkHttp HTTP cache

**取捨**：
- **優**：「資料寫入」與「時間戳更新」的原子性；Room `Flow` 查詢讓 UI 自動反映快取；強制 migration 保護使用者資料
- **劣**：多一個 DB 依賴；schema 匯出需要手動維護
- **為什麼不用 DataStore**：時間戳放 DataStore 無法與資料寫入原子操作，會出現「時間戳說新鮮但資料是舊的」競態
- **為什麼不用純 HTTP cache**：無法做「過期資料仍顯示」與收藏離線保證

---

## 5. 網路層：Retrofit 3 + OkHttp 5 + kotlinx.serialization

**選擇**：
- 三個 base URL → 三個 Retrofit instance（`@SpaceflightRetrofit`、`@OpenMeteoRetrofit`、`@DummyJsonRetrofit` qualifier）
- 共用一個 `OkHttpClient`（10 MB 磁碟快取、尊重 `Cache-Control`、timeout 設定）
- JSON 配置：`ignoreUnknownKeys = true`、`explicitNulls = false`、`coerceInputValues = true`
- Retrofit service 包在 `XxxRemoteDataSource` interface 後面

**替代方案**：
- Ktor client（KMP 友善但設定多）
- Moshi（需 KSP codegen 或 reflection）
- Gson（不理解 Kotlin null-safety）

**取捨**：
- **優**：Retrofit 業界標準；kotlinx.serialization 無反射、Kotlin-first、編譯期產生 serializer；OkHttp 磁碟快取自動尊重伺服器 Cache-Control
- **劣**：triple-Retrofit 配置比單一全域 base URL 稍複雜
- **選擇理由**：kotlinx.serialization 與 Navigation type-safe route 同套工具；Retrofit interface 測試用 MockWebServer 簡單

---

## 6. 圖片載入：Coil 3

**選擇**：
- `coil-compose` + `coil-network-okhttp`（共用 OkHttpClient）
- 記憶體快取 25%、磁碟快取 100 MB、crossfade
- `SubcomposeAsyncImage(loading = {...}, error = {...})` 為官方 slot API
- 收藏文章的圖片另存 `filesDir/{articleId}`，保證離線可看

**替代方案**：
- Glide（成熟，但 Compose 整合次要）
- Fresco（複雜，不適合此規模）

**取捨**：
- **優**：Kotlin/Coroutines/Compose-first；API 小；與 OkHttp 緊密整合
- **劣**：磁碟快取是 LRU、可被清除，不能作為收藏離線保證（所以額外下載到 filesDir）
- **SubcomposeAsyncImage 的必要性**：使用兩個獨立 Coil 請求判斷狀態會導致 placeholder 卡住；slot API 是唯一的 Coil3 官方做法

---

## 7. 並行模型：Coroutines + Flow

**選擇**：
- Repository 對外：`Flow<T>`（觀察快取）+ `suspend fun`（一次性動作）
- 注入 `@IoDispatcher CoroutineDispatcher`（只用在檔案 IO，如收藏圖片）；Room/Retrofit suspend API 已是 main-safe
- `@ApplicationScope CoroutineScope`（`SupervisorJob() + Dispatchers.Default`）：給不該隨畫面取消的工作（刷新、圖片下載）
- `AppClock` interface 注入現在時間；`NetworkMonitor` interface 注入網路狀態
- **Single-flight**：同一來源同時多個刷新只打一次網路
- 自訂 `suspendRunCatching {}`：捕捉例外但重新拋出 `CancellationException`（`runCatching` 會吞掉取消）

**替代方案**：
- RxJava（學習成本、與 Compose/Room 整合不如 Flow）
- Callback hell

**取捨**：
- **優**：Flow 是 Room/Compose/Lifecycle 原生語言；結構化並行讓取消正確傳遞；interface 注入使測試簡單
- **劣**：Coroutines 學習曲線（但已成 Android 業界標準）
- **到處 withContext(IO) 的陷阱**：Room/Retrofit 的 suspend API 本身已 main-safe，不需包 `withContext`；此反模式會造成不必要的分派開銷

---

## 8. 分頁：Paging 3 + RemoteMediator + Room PagingSource

**選擇（v2，決策變更）**：
- `Pager(PagingConfig(pageSize=20, prefetchDistance=5), remoteMediator=ArticleRemoteMediator, pagingSourceFactory={articleDao.pagingSource()})`
- **Room 是唯一資料來源**：UI 只讀 Room；RemoteMediator 是唯一會寫入的程式碼
- `initialize()`：新 Pager 建立時以 `FreshnessPolicy` 判斷是否拉第 1 頁
- **REFRESH**：成功後開 transaction，清空重建（`sortIndex` 從 0 重排），寫 remote_key，更新 sync_metadata
- **APPEND**：保留 keyset 游標（`published_at_lte`），濾掉邊界重複（`existingIds()`），無新 id 時結束分頁
- **異質混排**：天氣 hero 不進 `PagingData`（`LazyColumn` 先放獨立 `item {}`）；服務卡用 `insertSeparators` 與 `sortIndex` 規則插入

**替代方案（v1，被推翻）**：
- 手寫 keyset 分頁：Room `Flow<List<Article>>`、VM 持有 `AppendState` 狀態機、`snapshotFlow` 觸發 `loadNextPage()`、純函式 `FeedAssembler` 組合資料

### v1 原始決策：為什麼當初不用 Paging 3（Opus 撰寫）

v1 計畫（Opus）的選擇與理由：

**v1 的選擇**：
- Room 觀察整個快取文章列表 `Flow<List<ArticleEntity>>`（排序 `publishedAt DESC, id DESC`）
- 下一頁：`loadNextPage()` 以快取中**最舊的 `publishedAt`** 當游標，呼叫 `?published_at_lte=<cursor>&ordering=-published_at&limit=20`，用 id upsert 去重（邊界那幾筆一定會重複回來）
- 結束條件：回傳筆數 < pageSize，或「這一頁沒有任何新 id」（防止同一秒大量文章造成無限迴圈）
- ViewModel 持有 `AppendState`（Idle / Loading / Error / EndReached / Offline），UI 透過 `snapshotFlow { lastVisibleIndex }` 在距離底部 5 筆時呼叫 `onNearEnd()`；VM 在 Loading/EndReached 時忽略
- 異質 feed：純函式 `FeedAssembler.assemble(weather, articles, services): List<FeedItem>`

**為什麼用 keyset 而非 offset**：
- 文章持續新增，offset 分頁在「讀第 2 頁之前有新文章發布」時會重複或漏掉
- 已驗證 API 支援 `published_at_lte` / `published_at_lt`

**考慮過的替代方案**：
- Paging 3 + RemoteMediator + Room PagingSource
- v1 承認的 Paging 3 優點：記憶體視窗化、placeholder、內建 LoadState、retry

**不採用 Paging 3 的四個理由**：

(1) **異質混排難**：Paging 裡異質混排只能用 `insertSeparators`，而「每 N 篇插一張服務卡」需要位置資訊，`insertSeparators` 只有 before/after 參數，沒有 index，要嘛在 DB 維護 sortIndex、要嘛寫有狀態的 separator，都很彆扭

(2) **與新鮮度協調器重複決策**：`RemoteMediator.initialize()` 與 `REFRESH` 會變成第二個「決定何時刷新」的地方，和我們集中式的新鮮度協調器（§3）重複

(3) **快取規模小效益低**：本 App 快取上限數百筆，Paging 的記憶體視窗化效益低

(4) **手寫版本便於完整測試**：手寫版本讓 feed 組合成為可完整單元測試的純函式，append 狀態機也清楚可測（對應加分項「非同步邏輯測試」）

**代價**：
- 自己處理觸發時機、去重、重試、結束條件；沒有 placeholder
- 若 feed 規模變成無限長（例如 VOOM），會改回 Paging 3 並把服務卡改成 DB 內的一種 row type

### 為什麼改用 Paging 3？推翻過程

Wayne 審閱 v1 計畫後在對話中提問：「為什麼不使用page3、Okhttp3+Retrofit？」（其中 OkHttp/Retrofit 部分只是溝通落差：計畫本來就用 Retrofit 3 + OkHttp 5），並追問：「理由1,3,4 改用Paging3會變難作嗎」

主控（Claude Code）據此評估各理由——結論是四個理由都不會讓 Paging 3「做不出來」，只有理由 1 會多一些工作：

- **(1) 異質混排難**：天氣 hero 不必進 PagingData（LazyColumn 先放獨立 `item {}`）；服務卡位置由 mediator 寫入時指派遞增 `sortIndex` 決定，規則抽成純函式 `ServiceCardSlots.slotBefore()` 即可單獨測試
- **(2) 與協調器重複**：讓 `initialize()` 直接呼叫同一個 `FreshnessPolicy`；決策點仍是唯一的
- **(3) 快取規模小**：Paging 的價值是內建 LoadState/retry/append 觸發（省工），不是記憶體視窗化
- **(4) 手寫較好測**：官方 `paging-testing`（`TestPager`、`asSnapshot()`）足以測 PagingSource；RemoteMediator 用 Robolectric + in-memory Room 直接呼叫 `load()` 測

**Wayne 的決定**：「改用Paging3」。

**Opus 的行動**：據此修訂 v2 PLAN.md，分頁改為 Paging 3 + RemoteMediator（保留 keyset 游標於 APPEND）。

**實作過程**：Sonnet 在 Step 5 後暫停、awaiting v2 commit（b4a338e），隨後照新計畫繼續 Step 6–13。

**v2 取捨**：
- **優**：內建 append 觸發與 prefetch；`LoadState`（loading/error/end）；`retry()`/`refresh()`；記憶體視窗化；與 Room invalidation 整合；官方測試工具
- **劣**：Feed 畫面有兩個狀態來源（UiState + LoadState）；`insertSeparators` 需 DB 維護 `sortIndex`；RemoteMediator 邊界情況複雜；REFRESH 清空不適用「重疊合併」

---

## 9. Module 結構

**選擇**：
- 8 個 module + included build `build-logic`（convention plugins 消除重複）
- `core:domain`（純 Kotlin/JVM，無 Android 依賴）
- `core:data`（Retrofit、Room、repository 實作）
- `core:designsystem`（theme、共用 composable）
- `core:testing`（純 JVM，所有 fake 與 MainDispatcherRule）
- `feature:feed`、`feature:detail`、`feature:saved`（只依賴 domain + designsystem）
- `app`（唯一知道所有 route 的地方）

**替代方案**：
- (a) 單一 `app` module 以 package 分層
- (b) Now-in-Android 完整版（15+ module，過度）

**取捨**：
- **優**：依賴方向由 build 強制（feature 碰不到 Room/Retrofit）；domain 純 JVM 單元測試快速；測試 fake 集中共用
- **劣**：比單一 app module 多一點配置成本
- **選擇理由**：切在「有價值的邊界」而非機械式分層；convention plugins 消除重複設定（每個 module 的 `build.gradle.kts` 只剩幾行）

---

## 10. 導覽：Navigation Compose Type-safe Routes

**選擇**：
- Navigation 2.10.1 type-safe routes（`@Serializable data object FeedRoute`、`@Serializable data class ArticleDetailRoute(val articleId: Long)`）
- 每個 feature 提供 extension（如 `NavGraphBuilder.feedScreen(...)`）；feature 之間不互相依賴
- ViewModel 讀參數用 `savedStateHandle.get<Long>("articleId")`，**不用** `savedStateHandle.toRoute<>()`

**替代方案**：
- Navigation 3（較新，API 仍演進中）
- Compose Destinations（第三方）
- String-based route（型別不安全）

**取捨**：
- **優**：型別安全；編譯期驗證；Kotlin 2.4+ 內建支援
- **劣**：新 API，文檔較少
- **SavedStateHandle.toRoute() 的陷阱**：需要 Android `Bundle`，純 JVM 單元測試會失敗

---

## 11. 新鮮度策略（FreshnessPolicy）

**選擇**：
- 集中化決策：`FreshnessPolicy` 純函式，依 `(source, lastSuccessAt, networkType, trigger)` → `Decision`
- TTL 由 `TtlConfig` 統一管理（天氣/文章/服務卡各自配置）
- unmetered（Wi-Fi）網路 TTL 更短（便宜），metered（行動網路）更長（省流量）
- 下一頁 (APPEND) 不受 TTL 管（使用者主動向下捲即明確意圖）
- **Stale-while-revalidate**：過期資料照常顯示，背景刷新（無 spinner）

**替代方案**：
- 各 repository 自己決定何時刷新（分散、難以統一策略）
- 固定 TTL（無法區分 unmetered/metered）
- 總是刷新第一頁（浪費流量）

**取捨**：
- **優**：單一決策點，策略變更只改一個地方；TTL 分層合理（天氣分鐘級、文章小時級、服務卡天級）；節流器內建於 TTL 本身（即使快速切換 App 也不會多打請求）
- **劣**：需要理解 TTL 與 trigger 的交互
- **選擇理由**：符合真實場景（文章以小時計、天氣以分鐘計）；可測試（純函式）

---

## 12. 資料來源選擇（為何不用 TMDB）

**選擇**：
- Spaceflight News API（文章）
- Open-Meteo（天氣）
- DummyJSON（服務卡）
- **不用 TMDB**

**理由**：
- TMDB 需要 API key → 面試官 clone 不能直接跑，違反「一行指令起來」
- 三個 free API 組合展示「新鮮度策略對不同更新頻率的來源」（分鐘 vs 小時 vs 天）
- 本地 mock 也不扣分（題目說「免註冊、免 key，任選或混用；本地 mock 也不扣分」）

---

## 13. 離線與收藏

**選擇**：
- `bookmarks` 表存完整快照（不能只存 id，因為 feed 快取會被清）
- 收藏時複製文章全部欄位（title、summary、newsSite、url、imageUrl、authors、publishedAt、savedAt）
- 圖片在 `@ApplicationScope` 非同步下載到 `filesDir/bookmark_images/{articleId}`
- 下載失敗時保留 `localImagePath = null`；之後在 `FOREGROUND`/`NETWORK_RESTORED` 重試
- `SubcomposeAsyncImage` 優先載入 `localImagePath`，fallback 到 `imageUrl`

**替代方案**：
- 只存 id + 圖片只靠 HTTP 快取（會失效）
- 不下載圖片（違反離線需求）

**取捨**：
- **優**：快取被清時仍能查到收藏；圖片永続化保證；下載非同步不擋主路徑
- **劣**：`filesDir` 占用空間；下載失敗競態需雙重檢查（取消收藏時先查 DB 再刪檔）
- **選擇理由**：使用者收藏是明確動作，投資儲存空間合理

---

## 14. 搜尋實作

**選擇**：
- Saved 頁本地搜尋（Room SQL `LIKE`，WHERE 子句過濾）
- `query.debounce { if (it.isBlank()) 0L else 300L }` 依值決定延遲（空字串不等待，加快冷啟動）
- 防止 LIKE 萬用字元注入（`%`、`_` 視為字面值，用 `escape` 子句）

**替代方案**：
- 遠端搜尋（API `search=`）
- 全文搜尋索引（FTS）
- 不搜尋

**取捨**：
- **優**：本地搜尋離線可用；無網路延遲；邏輯簡單（SQL WHERE）
- **劣**：無法排序（本地資料順序 ≠ 相關度排名）
- **選擇理由**：遠端搜尋需要另套分頁 + debounce 取消邏輯，複雜度高；本地搜尋「離線可用」與主題一致

---

## 15. 測試策略

**選擇**：
- 層級化：Domain (pure function) → DAO (Robolectric) → Repository (Fake remote) → ViewModel (Fake repo)
- Domain 用純函式測試 (JVM 快速)；DAO 用 Robolectric + in-memory Room
- RemoteMediator 用 Robolectric + FakeClock + FakeNetworkMonitor
- Paging 轉換用 `paging-testing` 的 `TestPager` + `asSnapshot()`
- ViewModel 用 `runTest` + `MainDispatcherRule` + StateFlow 驗證

**替代方案**：
- 只做 ViewModel 測試（coverage 不足）
- 用 MockK 做 unit test（反而更複雜）
- 只做 Compose UI 測試（state 邏輯無法驗證）

**取捨**：
- **優**：各層邊界清楚，可獨立測試；fake 優於 mock（更簡單、更快）；官方工具（paging-testing）
- **劣**：Robolectric 設定複雜（SDK 35 而非 36）；多層測試維護成本
- **選擇理由**：層級化測試提高信心；避免 mock explosion；Robolectric SDK 版本選擇見 NOTES.md

---

## 實作中的調整

### Robolectric SDK 版本

SDK 36 在 JDK 21 下 `ApplicationSharedMemory` 反射失敗（NOTES.md Step 5）。改用 SDK 35（Robolectric 支援範圍內、minSdk..targetSdk 範圍內）完全通過。

### ArticleRepository.observeLastSuccessAt()

FeedUiState.lastUpdated 需要文章最後成功更新的時間。文章沒有像 Weather 的每項 `fetchedAt`，而 `sync_metadata` 在 core:data，feature:feed 無法直接碰。加這個方法（讀 sync_metadata 篩 ARTICLES）是最小、語意清楚的做法。

### FeedImage 與 SubcomposeAsyncImage

Step 9 模擬器驗證發現：原始 FeedImage 用兩個獨立 Coil 請求判斷狀態，其中一個永不被 layout，Coil 無法解析尺寸 → 回報 Empty/Error → placeholder 卡住。改用 `SubcomposeAsyncImage(loading/error slots)` 解決。

### 多日天氣預報

主控 review 截圖時發現 WeatherHeroCard 只有今日 H/L，缺需求要求的多日預報。資料層已有 `Weather.daily`，只是 UI 沒用。Sonnet 補上 commit e475bdd 新增 forecast row。
