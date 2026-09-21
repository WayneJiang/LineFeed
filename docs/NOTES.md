# 實作筆記（隨 commit 累積，最後彙整進 DECISIONS.md / AI_USAGE.md）

格式：問題 / 原因 / 解法。只記錄實際發生過的事。

## Step 1：bootstrap gradle wrapper, version catalog and convention plugins

1. **問題**：`build-logic/convention` 內 `AndroidConventions.kt` 對 `CommonExtension`
   呼叫 `defaultConfig { ... }` / `compileOptions { ... }` 出現
   `Unresolved reference 'defaultConfig'` 等編譯錯誤。
   **原因**：AGP 9 的 `com.android.build.api.dsl.CommonExtension` 只提供
   `getDefaultConfig()` / `getCompileOptions()` 屬性（無對應的
   `defaultConfig(Function1<...>)` block 多載）；block 語法的多載只存在於
   `ApplicationExtension` / `LibraryExtension` 各自的 `defaultConfig`。用 `javap`
   反編譯 `gradle-api-9.4.0.jar` 確認後改用 `defaultConfig.apply { minSdk = 24 }` /
   `compileOptions.apply { ... }`（屬性 + `.apply{}`，而不是呼叫不存在的 block 函式）。

2. **問題**：`app/build.gradle.kts` 出現大量 `Unresolved reference 'androidx'`
   （`libs.androidx.core.ktx` 等 typesafe accessor 全部失效）。
   **原因**：`build-logic/convention` 內原本定義了頂層擴充屬性
   `val Project.libs: VersionCatalog`。凡是套用了任一 convention plugin 的模組，
   該 plugin 所在 jar 的類別（含這個頂層 `libs` 屬性）都會進入該模組 build script
   的編譯 classpath，**蓋掉**了 Gradle 自動產生、型別安全的 `libs`
   （`LibrariesForLibs`）accessor——編譯器解析 `libs.androidx.core.ktx` 時選到了
   回傳泛型 `VersionCatalog` 的那個屬性，而非型別安全版本。
   **解法**：把 convention plugin 內部用來讀 catalog 的屬性改名為
   `Project.versionCatalog`（不再叫 `libs`），避免與消費端 script 的自動產生
   accessor 撞名。這是一個「plugin 實作細節命名不能跟 Gradle 保留給使用端的名稱
   相同」的坑，非 PLAN.md 原先預期，但不影響 PLAN 的其餘設計。

3. **問題**：`./gradlew unitTest` 中 `:core:data:testDebugUnitTest` 失敗，錯誤是
   Gradle 9 的 `There are test sources present and no filters are applied, but
   the test task did not discover any tests to execute.`，即使
   `core/data/src/test` 目錄完全是空的（沒有任何 `.kt` 檔）。
   **原因**：`linefeed.android.library` convention plugin 依 PLAN §7 設定
   `testOptions.unitTests.isIncludeAndroidResources = true`（之後 Robolectric
   測試需要）。這個設定會讓 AGP 對 `debugUnitTest` variant 產生一個空的
   `R.class`（即使模組完全沒有 `res/`、也沒有任何測試原始碼），這顆自動產生的
   class 檔仍會被打進 test classes jar；Gradle 9 預設的
   `failOnNoDiscoveredTests` 一看到 test classes 目錄「非空」就認定「有測試原始碼」，
   但掃不到任何 `@Test`，於是直接判定為設定錯誤並讓 build 失敗。
   PLAN §12 原本提醒的是「不要留空的 `src/test` 目錄」，但這裡連目錄都刪乾淨了仍會
   炸——根因其實是 `isIncludeAndroidResources` 這顆開關本身在完全沒有測試檔案時
   的副作用，PLAN 沒有預期到。
   **解法**：在 `AndroidLibraryConventionPlugin` 對所有
   `Test` task 加上 `failOnNoDiscoveredTests.set(false)`。真正有測試的模組
   （Step 3 起）一律能正常被發現與執行，此設定只在「模組目前沒有任何測試」時避免
   假性失敗；不會遮蔽真正的測試失敗（測試失敗时 task 仍會 FAILED）。
   同時把先前誤建的 `core/data/src/test`、`core/domain/src/test` 空目錄整個刪除，
   遵守 PLAN §12「不要建立空的 src/test」。

## Step 3：add domain models, freshness policy and refresh triggers

- **計畫變更**：實作到一半時，PLAN.md 被 Wayne/Opus 修訂為 v2（`docs/PLAN.md` commit
  `b4a338e docs: revise plan to use paging 3 with remote mediator`）：分頁從手寫
  keyset 改為 Paging 3 + `RemoteMediator`。coordinator 指示 Step 3（本步）直接依
  v2 的 §6/§7 實作，不必先做 v1 版再改。因此本步 `core:domain`／`core:testing`
  與 v1 的差異：新增 `FeedArticle` model、`ArticleRepository` 改為
  `feedPagingData(): Flow<PagingData<FeedArticle>>` + `observeArticle(id)`（移除
  `observeFeed()`/`loadNextPage()`/`LoadMoreResult`）、`RefreshStatus` 新增
  `articleRefreshRequestId`、`core:domain` 以 `api` 依賴 `androidx.paging:paging-common`
  （KMP artifact，有 JVM target，純 JVM module 可用）。這些改動只影響尚未 commit
  的工作，未曾以 v1 形式進版控。

- **問題**：`FreshnessPolicyTest` 一開始想直接重用 `core:testing` 的 `FakeClock`。
  **原因**：module 依賴方向是 `core:testing --api--> core:domain`；若
  `core:domain` 的 `test` source set 又 `testImplementation(project(":core:testing"))`，
  會形成 `core:domain -> core:testing -> core:domain` 的專案依賴環。
  **解法**：`core:domain` 的測試改成在測試檔內自帶一個極簡的 `FixedClock`
  （`private class FixedClock(private val instant: Instant) : AppClock`），不依賴
  `core:testing`。`core:testing` 的 fake 只給「依賴 `core:domain` 的其他 module」
  （`core:data`、`feature:*`）在測試裡用，`core:domain` 自己的測試不需要、也不能用它。

- **問題**：`SingleFlightTest` 的「取消其中一個呼叫者，共享工作不受影響」測試第一次
  跑會斷言失敗（`expected:<42> but was:<-1>`），也就是「存活的呼叫者」自己重新執行
  了 block，而不是拿到被取消那個呼叫者共享的結果。
  **原因**：`runTest` 預設用 `StandardTestDispatcher`，`async { }` 建立的協程不會
  立即開始執行（要等排程器有機會跑）。測試在建立兩個 `async` 之後立刻呼叫
  `cancelled.cancel()`，這時 `cancelled` 協程可能根本還沒開始執行、根本沒有機會
  搶到 `SingleFlight` 的「這個 key 由我負責跑」名額，於是變成 `survivor` 自己去跑
  （呼叫自己的 block 回傳 `-1`），而不是共用 `cancelled` 應該要建立的共享工作。
  **解法**：在 `cancelled.cancel()` 之前呼叫 `testScheduler.advanceUntilIdle()`，
  讓兩個協程先跑到各自的第一個暫停點（也就是都已經呼叫過
  `SingleFlight.run()`、其中一個已經真的登記為「負責執行者」並在
  `deferred.await()` 上暫停），再取消其中一個，這樣才真正測到「取消呼叫端不影響
  共享工作」這件事，而不是「取消得夠早，工作根本沒開始」的假陽性。

## Step 4：add retrofit clients and dtos for spaceflight, open-meteo and dummyjson

- **問題**：`NetworkModule.kt` 一開始照 PLAN.md 上寫的
  `import retrofit2.kotlinx.serialization.asConverterFactory` 寫，編譯出現
  `Unresolved reference 'kotlinx'`。
  **原因**：`converter-kotlinx-serialization:3.0.0` 這個 artifact 的實際 package
  是 `retrofit2.converter.kotlinx.serialization`（多了一層 `converter`），不是
  PLAN.md 寫的 `retrofit2.kotlinx.serialization`；用 `javap` 反編譯該 jar
  （`retrofit2/converter/kotlinx/serialization/*.class`）確認。擴充函式本身叫
  `asConverterFactory`（定義在 `Factory.kt`，`@file:JvmName("KotlinSerializationConverterFactory")`
  只是給 Java 呼叫端看的 JVM 名稱，Kotlin 這邊呼叫的是原本的擴充函式名）。
  **解法**：改成
  `import retrofit2.converter.kotlinx.serialization.asConverterFactory`，其餘
  `json.asConverterFactory(mediaType)` 呼叫方式不變。已在本 commit 內驗證
  `:core:data:compileDebugKotlin` 成功。

- **問題**：`mockwebserver3` 的 `MockWebServer` 沒有 `shutdown()` 方法。
  **原因**：OkHttp 5 的 `mockwebserver3` 套件把 `MockWebServer` 改成實作
  `java.io.Closeable`，關閉伺服器的方法是 `close()`，不是舊版 mockwebserver（`okhttp3.mockwebserver`）
  的 `shutdown()`。
  **解法**：測試輔助類別 `MockApiTestHarness.shutdown()` 內部呼叫
  `server.close()`。

- **測試資料誠實聲明**：`core/data/src/test/resources/fixtures/` 下
  `spaceflight_articles_page1.json`、`open_meteo_forecast.json`、
  `dummyjson_products.json` 是本步驟當下用 `curl` 對三個真實 API 現抓的回應（見
  PLAN.md §6 記載的路徑與參數）；`spaceflight_articles_edge_cases.json`、
  `dummyjson_products_missing_brand.json` 是**手寫**的最小 JSON，用來涵蓋
  PLAN.md §6.1/§6.3 提到但這次真實抓取沒剛好抓到的邊界情況（`image_url` 為
  `null`/空字串、`summary` 為空字串、`updated_at` 缺失、未知欄位、`brand`
  缺失）——欄位名稱與型態都對照真實 schema 手刻，但内容本身是捏造的測試資料，
  誠實記錄於此，不混充為真實抓取結果。

## Step 5：add room database for feed cache, bookmarks and sync metadata

- **問題（最花時間的一個）**：所有 Robolectric 測試（`FeedArticleDaoTest`、
  `RemoteKeyDaoTest`、`BookmarkDaoTest`）在 `@Config(sdk = [36])` 下 100% 失敗，
  錯誤是
  `java.lang.RuntimeException: Failed to interact with raw FileDescriptor internals; perhaps JRE has changed?`，
  發生在 Robolectric 初始化 `com.android.internal.os.ApplicationSharedMemory.create(...)`
  的階段（測試方法本身還沒開始跑）。
  **原因排查過程**：
  1. 一開始懷疑是 JDK 17+ 模組系統擋掉了 Robolectric 對 JDK 內部
     `java.io.FileDescriptor` 的反射，依 Robolectric 官方文件在
     `AndroidLibraryConventionPlugin` 對所有 `Test` task 加了一整組
     `--add-opens`（`java.lang`、`java.util`、`java.io`、`java.security`、
     `java.text`、`java.desktop/java.awt.font`）。加了之後**還是同樣的錯誤**，
     代表不是單純的模組存取權限問題。
  2. 改用 `@Config(sdk = [35])` 測試同一批案例，**完全通過**（連同一開始就對的
     mapper/network 測試在內）。可以確定問題是 Robolectric 4.17 對 SDK 36
     （Android 16 對應的 `android-all-instrumented`）新增的
     `ApplicationSharedMemory` 這個 App 啟動流程 shadow，在本機 JDK 21 環境下
     其反射寫入 `FileDescriptor` 內部欄位的方式本身就會丟例外，`--add-opens`
     只解決「JVM 擋你反射」，解決不了「Robolectric 寫死的欄位存取方式與這個
     JDK 版本的內部佈局對不上」這類問題。
  **解法**：`core:data` 的 Robolectric 測試改用 `@Config(sdk = [35])`（不是
  PLAN.md §9.1 建議的 `[36]`）。SDK 35 一樣在 Robolectric 4.17 的官方支援範圍
  內、一樣 ≥ minSdk 24、≤ targetSdk 36，只是不觸發這個特定的新增 shadow。
  這是**依 coordinator 指示不修改 docs/PLAN.md**、僅在此記錄的一個偏離；
  README/DECISIONS 階段應該把「為什麼 DAO 測試用 sdk=35 而不是 36」講清楚。
  `AndroidLibraryConventionPlugin` 裡新增的 `--add-opens` 集合本身仍然保留
  （對其他潛在的 Robolectric/JDK21 反射問題有防禦價值，且沒有副作用）。

- **問題**：`RemoteKeyDaoTest`/`FeedArticleDaoTest` 一開始用
  `Room.inMemoryDatabaseBuilder(...).setQueryCoroutineContext(UnconfinedTestDispatcher())`
  （依 PLAN.md §9.1 的建議），跑出
  `UnsupportedOperationException: Function UnconfinedTestCoroutineDispatcher.dispatch can only be used by the yield function.`
  **原因**：Room 產生的 DAO 實作內部用 `withContext(queryCoroutineContext)`
  分派每個 suspend 查詢；`kotlinx-coroutines-test` 的 `UnconfinedTestDispatcher`
  設計上只給 `runTest` 自己的協程機制在特定情境（`yield`）呼叫，一般的
  `withContext` 分派方式打進去會直接丟例外——這與「在 `runTest` 內驅動 VM 底下
  真正的協程」是不同的使用情境，PLAN.md 這條建議在 Room 的場景下不成立。
  **解法**：`RoomTestDatabase.kt` 的 `createTestDatabase()` 改用真正的
  `Dispatchers.IO`（而非任何 `TestDispatcher`）當 Room 的
  `queryCoroutineContext`；DAO 測試不需要虛擬時間，用 `runTest`
  （多數案例）或 `runBlocking`（`FeedArticleDaoTest` 裡需要真實時間輪詢
  `pagingSource.invalid` 的那個案例）皆可正常搭配真實 dispatcher 運作。

- **測試涵蓋的取捨**：`FeedArticleDaoTest` 的「bookmark 變動使 pagingSource
  invalidate」用真實時間輪詢（最多 3 秒）斷言 `pagingSource.invalid`，而不是
  斷言確切耗時——Room 的 `InvalidationTracker` 是非同步的背景執行緒回呼，硬性
  斷言時間點容易在較慢的 CI 機器上變成假陰性。

## 一般記錄
- 本機環境確認：JDK 21 (Corretto)、Android SDK 已有 platforms 35/36/37.0、
  `~/.gradle/wrapper/dists` 已有 gradle-9.7.1-bin 快取、AGP 9.4.0 jar 已在
  `~/.gradle/caches/modules-2` 中（與 PLAN §8.1 的驗證證據一致）。
- `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar` 從
  `~/Github/MyPoke`（同版本 9.7.1）複製後改寫 `gradle-wrapper.properties`，
  未使用官方 `gradle wrapper` 指令重新產生（效果相同，皆為 9.7.1 bin distribution）。

## Step 6：add paging 3 remote mediator with keyset append for articles

- **發現**：Step 3~5 的 commit 其實已經把 PLAN.md v2 §6 要求的 domain/data 調整（`FeedArticle`、
  `ArticleRepository.feedPagingData()`、`feed_articles.sortIndex`、`remote_keys` 表與
  `RemoteKeyDao`、`FeedArticleDao.pagingSource()`/`insertAll(IGNORE)`/`maxSortIndex`/`clearAll`、
  `ArticleMappers.toEntity(sortIndex, fetchedAt)`）直接做好了，不是「先寫 v1 keyset 手寫分頁再改」。
  本步驟因此只需要新增 `ArticleRemoteMediator`、`OfflineFirstArticleRepository`、
  `SystemAppClock` 與對應測試，PLAN.md 表格中「調整」那段對本專案是 no-op。
- **問題**：`OfflineFirstArticleRepository`（public class）建構子直接持有 `internal class
  ArticleRemoteMediator` 型別參數，Kotlin 編譯報
  `'public' function exposes its 'internal' parameter type`。
  **解法**：`OfflineFirstArticleRepository` 保留 public class（實作 public 的
  `ArticleRepository` interface），但建構子本身標成 `internal constructor`——外部只透過
  `ArticleRepository` 介面使用它，建構子不需要對外可見，測試與 Hilt 綁定都在 `core:data`
  模組內即可存取。
- **偏離 PLAN.md**：Step 6 表格寫「`SystemAppClock` 與相關 Hilt 綁定」，但 `AppClock`／
  `ArticleRepository` 的 `@Binds` 綁定需要 `NetworkMonitor` 的真正實作
  （`ConnectivityNetworkMonitor`）才能讓 Hilt 圖完整可解析，而 `ConnectivityNetworkMonitor`
  依 PLAN.md 排在 Step 7。本步驟只新增 `SystemAppClock` 類別本身（`@Inject constructor`），
  尚未加入任何 `@Binds`／`DataModule`；`AppClock`、`ArticleRepository`、`NetworkMonitor` 的
  Hilt 綁定改到 Step 7 的 `DataModule` 一次到位，與 Weather/ServiceCard repository 綁定一起
  加，避免出現「模組已建立但綁定不完整」的中間態。已同步在 PLAN.md §10 Step 6/7 加註。
- **測試涵蓋**：新增 `ArticleRemoteMediatorTest`（initialize 依 FreshnessPolicy 決定 launch/skip、
  離線一律 skip、REFRESH 清空重建＋游標寫入、REFRESH 回傳空頁即結束分頁、APPEND 在無
  remote key 時不打網路、APPEND 沿用游標並延續 sortIndex、APPEND 無新 id 即結束分頁且不重複
  寫入、結束分頁後 APPEND 不再打網路、PREPEND 直接視為結束、載入失敗回傳
  `MediatorResult.Error` 並寫入 `sync_metadata` 的 `lastError`）、
  `OfflineFirstArticleRepositoryTest`（`asSnapshot()` 驗證排序與收藏聯集、`observeArticle`
  以 Turbine 驗證「先 null 後有值」）、`RemoteKeyDaoTest`（get/upsert/clear）。

## Step 7：add weather, service and bookmark repositories with refresh coordinator

- **問題**：`AppRefreshInitializer`（`app` module）建構子想直接注入
  `com.waynejiang.linefeed.core.data.di.ApplicationScope`／`IoDispatcher` 這類 qualifier，一開始
  猶豫要不要把它們搬到 `core:domain`。後來維持放在 `core:data/di/Qualifiers.kt`：`app` 已經
  依賴 `core:data`，且這兩個 qualifier 本來就是「`core:data` 內部如何跑背景工作」的實作細節，
  domain 不需要知道，只需要透過 `FeedRefresher`/`NetworkMonitor` 介面互動。
- **偏離 PLAN.md**（延續 Step 6 的記錄）：`AppClock`、`ArticleRepository`、`WeatherRepository`、
  `ServiceCardRepository`、`BookmarkRepository`、`FeedRefresher`、`NetworkMonitor`
  以及三個 `RemoteDataSource` 的 `@Binds` 全部在本步驟的 `DataModule` 一次到位（如 Step 6
  NOTES 所述）；`app:assembleDebug` 通過即代表 Hilt 圖完整可解析（`OfflineFirstArticleRepository`
  → `ArticleRemoteMediator` → `ArticleRemoteDataSource`/`AppClock`/`FreshnessPolicy`/`NetworkMonitor`
  全部有 binding）。
- **設計取捨**：`DefaultFeedRefresher.shouldFetch`/`skipReasonFor` 各自呼叫一次
  `FreshnessPolicy.evaluate`（同一個來源、同一組參數，被跳過的來源等於算兩次）。
  `FreshnessPolicy` 是純函式、無副作用，多算一次只是些微 CPU 成本換取程式碼更直觀（一個函式只回答
  一個問題），在來源數量固定為 3 的情境下不值得為省一次呼叫而讓呼叫端自己快取判斷結果。
- **測試涵蓋**：`OfflineFirstWeatherRepositoryTest`／`OfflineFirstServiceCardRepositoryTest`
  （refresh 成功寫快取＋`sync_metadata`、失敗回 `SourceResult.Failed` 且不動快取、服務卡「整批替換
  而非合併」）、`DefaultBookmarkRepositoryTest`（收藏/取消收藏快照、savedAt 取自注入的 clock、
  查詢比對 title/newsSite 且不分大小寫、查詢字串含 `%`/`_` 等 LIKE 萬用字元時視為字面值）、
  `DefaultFeedRefresherTest`（USER_PULL 無視新鮮度全部刷新、FOREGROUND 對新鮮來源跳過／對過期
  來源刷新、一個來源失敗不影響另一個、離線時全部 Skip 且不打網路、只有 FOREGROUND/NETWORK_RESTORED
  且文章過期才會讓 `articleRefreshRequestId` 遞增、USER_PULL 不會動 `articleRefreshRequestId`、
  完成後 `status.inFlight` 清空）。
- 尚未做（依計畫排到後續步驟）：`feature:feed` 尚未存在，`AppRefreshInitializer.start()` 目前沒有
  被任何畫面/測試驗證實際的 Logcat 行為，只驗證了 Hilt 圖可解析與 `app:assembleDebug` 成功；
  手動的模擬器驗證留到 Step 9（`feature:feed` 完成後）一起做。

## Step 8：add material 3 theme with dark mode and shared state components

- **問題**：`FeedImage` 原本用 `val state by painter.state` 讀 Coil3 `AsyncImagePainter` 的狀態，
  編譯報 `DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE`（列出 `State<T>.getValue` 等候選但都不適用）。
  **原因**：命名沖突——委託目標型別 `AsyncImagePainter.State` 與 Compose 的
  `androidx.compose.runtime.State` 撞名，`by` 委託解析在這個情境下找不到正確的
  `getValue` 多載。
  **解法**：不用屬性委託，直接寫 `val state: AsyncImagePainter.State = painter.state.value`——
  在 Compose 中讀取 `State<T>.value` 一樣會註冊快照讀取、觸發重組，行為與 `by` 委託等價。
- **設計**：色票手刻（非套用 Material Theme Builder 產生的完整 tonal palette），刻意選一個
  與 LINE 官方品牌綠（`#06C755`）明顯不同的深綠（`Green40 = #13712E`），避免「顏色本身」被誤認
  為冒用品牌；不使用 dynamic color（PLAN.md §0 明訂）。
  `RelativeTimeFormatter` 固定用 `Locale.US` 格式化絕對日期（"Sep 18"），不是跟隨裝置語系——
  這是為了讓 PLAN.md §7.3 給的確切格式範例可被單元測試斷言，International 化留在 README
  已知限制。
- **測試**：`RelativeTimeFormatterTest` 覆蓋 <1 分鐘/分鐘/小時/天/滿 7 天轉絕對日期、未來時間
  （clock skew）也轉絕對日期而不是負數、1 分鐘的邊界值。
- 尚未做：`SkeletonCard` 的 shimmer 目前是簡單的 alpha 呼吸動畫，不是掃光效果；`FeedImage`
  的 placeholder/error 圖示相同（Icons.Filled.Image），detail 頁大圖版面留給 Step 10 依實際
  使用情境調整（可能需要不同 aspect ratio）。

## Step 9：add paged heterogeneous feed with weather hero and service card separators

- **問題（最花時間）**：`FeedViewModelTest` 一開始用 `viewModel().feedItems.asSnapshot()`
  斷言服務卡有插入，跑起來 `kotlinx.coroutines.test.UncompletedCoroutinesError: After waiting
  for 1m, the test body did not run to completion`。
  **原因**：`feedItems` 是 `articleRepository.feedPagingData().cachedIn(viewModelScope)...`——
  `viewModelScope`（`SupervisorJob() + Dispatchers.Main.immediate`）是一個獨立於 `runTest`
  `TestScope` 的協程階層；`asSnapshot()` 需要等它收集到「穩定」為止，而 Fake 版
  `ArticleRepository`/`ServiceCardRepository` 底層是永不完成的 `MutableStateFlow`，兩者疊加造成
  `asSnapshot()` 永遠等不到可以視為完成的訊號。
  **排查過程**：先移除 `cachedIn`（直接用 `articleRepository.feedPagingData().combine(...)`）
  仍然 hang，證實問題不是 `cachedIn` 本身，而是「用永不完結的 `MutableStateFlow` 當
  `asSnapshot()` 的上游」這個測試手法本身有問題（`FeedPagingTransformsTest` 之所以能用
  `asSnapshot()` 成功，是因為它用 `flowOf(...)`——一個發射一次就結束的 Flow）。
  **解法**：`FeedViewModelTest` 不對 `feedItems` 做 `asSnapshot()` 驗證；`toFeedItems()` +
  `ServiceCardSlots` 的邏輯已經由 `FeedPagingTransformsTest`（用 `flowOf`）完整覆蓋，
  `FeedViewModelTest` 只覆蓋 `uiState` 的邏輯（天氣狀態機、offline、pendingArticleRefreshId、
  bookmark 切換、pull-to-refresh 訊息）。這是**測試手法的取捨**，不是不測——同一段邏輯已經
  在別的測試被測過，重複用一個會 hang 的手法測第二次沒有增加信心，只有增加維護成本。
- **問題**：`FeedViewModelTest` 的「outdated weather」案例一開始 `clock.advanceBy(4h)` 後把
  `fetchedAt` 設成 `clock.now().minus(4h)`——算出來剛好等於上一次設定的 `fetchedAt`，導致
  `MutableStateFlow` 判斷「新值等於舊值」而不重新發射，Turbine `awaitItem()` 逾時。
  **解法**：改成在 `advanceBy` 之前就先算好一個明確不同的 `fetchedAt`，避免兩次 `Weather`
  值意外結構相等。
- **問題**：`FeedImage`／`Icons.Filled.*` 在 `app`、`feature:feed` 都各自需要
  `material-icons-extended`，一開始只在 `core:designsystem` 加，`app`/`feature:feed` 編譯
  `LineFeedApp.kt`/cells 時噴 `Unresolved reference 'icons'`。
  **解法**：各自在 `feature/feed/build.gradle.kts`、`app/build.gradle.kts` 也加
  `libs.androidx.compose.material.icons.extended`（`core:designsystem` 的 `implementation`
  依賴不會傳遞給下游模組）。
- **問題**：`NavigationBarItem` 的選中判斷 `NavDestination.hasRoute(KClass<*>)` 一開始漏了
  import，寫成 fully-qualified 呼叫又對錯多載（`hasRoute(String, SavedState?)`）；反編譯
  `navigation-common-android-2.10.1-sources.jar` 確認正確的擴充函式簽章是
  `NavDestination.Companion.hasRoute(route: KClass<T>): Boolean`，改成明確 import
  `androidx.navigation.NavDestination.Companion.hasRoute` 後正常解析。
- **偏離 PLAN.md**：`ArticleRepository` 新增 `observeLastSuccessAt(): Flow<Instant?>`（PLAN.md
  §7.2/§7.5 原本的介面沒有這個方法）。原因：`FeedUiState.lastUpdated`「文章 lastSuccessAt」需要
  一個資料來源，但文章不像天氣/服務卡那樣有 `Weather.fetchedAt`/`ServiceCard` 直接帶時間戳，
  而且文章的 `sync_metadata` 只存在 `core:data`，`feature:feed` 不能直接碰。加這個方法（底層讀
  `SyncMetadataDao.observeAll()` 篩 `ARTICLES` 那筆）是最小、語意最清楚的做法。已同步更新
  `FakeArticleRepository`、`OfflineFirstArticleRepositoryTest`。
- **簡化**：`feature:feed` 沒有 `retrofit` 依賴（架構刻意不讓 feature 碰 `core:data`），所以
  `FeedScreenState.kt` 裡的 `Throwable.toFeedAppError()` 是一個比 `core:data`
  `ErrorMappers.toAppError()`更粗略的版本，認不出 `retrofit2.HttpException`，一律落到
  `AppError.UNKNOWN`（不是 `SERVER`）。已在程式碼註解與 README「已知限制」處記錄。
- **手動驗證**：本步驟的模擬器驗證（冷啟動 fresh/stale、下拉、捲到底、飛航模式）併入 Step 11
  完成後一起做（見該步筆記）。

## Step 10：add article detail screen with bookmark toggle

- **偏離 PLAN.md**：`BookmarkRepository` 新增 `observeSavedArticle(id): Flow<SavedArticle?>`
  （PLAN.md §7.6 原文列了兩個選項，「建議在 BookmarkRepository 加 observeSavedArticle(id)」——
  採用建議選項，不是無中生有的偏離，這裡記錄是因為它跟 Step 9 的
  `ArticleRepository.observeLastSuccessAt()` 一樣，都是「PLAN 列出兩個做法時選一個」的決策點）。
  `DetailUiState.Content` 也比 PLAN.md §5.5 原始定義多一個 `localImagePath: String?` 欄位——
  詳情頁離線時要顯示收藏文章「已下載到本機」的圖片（PLAN.md §4.1/§7.6 都提到這個需求），
  但 `Article` 本身故意不帶 `localImagePath`（避免它滲進不需要離線圖片的地方，例如 Feed 列表），
  所以只能讓 `DetailUiState.Content` 自己多帶一個欄位。
- **`SavedStateHandle` 用法**：依 PLAN.md §11 風險清單「`SavedStateHandle.toRoute()` 在 JVM 測試
  失敗」，`ArticleDetailViewModel` 用 `savedStateHandle.get<Long>("articleId")`（型別安全導航的
  參數仍然是用屬性名稱存進 `SavedStateHandle`，用純 key 讀取一樣讀得到，不需要
  `toRoute()`/Android Bundle）。測試中直接 `SavedStateHandle(mapOf("articleId" to id))` 構造，
  不需要 Robolectric。
- **問題**：`feature:detail` 一開始沒加 `material-icons-extended`，`ArticleDetailScreen.kt` 用
  `Icons.AutoMirrored.Filled.ArrowBack` 編譯報 `Unresolved reference 'icons'`——與 Step 9 遇到的
  同一類問題（`core:designsystem` 的 `implementation` 依賴不會傳遞給下游），解法相同：
  在 `feature/detail/build.gradle.kts` 自己加這個依賴。
- **測試涵蓋**：`ArticleDetailViewModelTest`——不存在時 NotFound、feed 快取有資料時反映
  isOffline、feed 快取沒有但收藏快照有時 fallback 到快照（含離線後 feed 快取被清空的情境）、
  `localImagePath` 只從收藏快照來（不是 feed 快取）、只在 feed 快取而未收藏時 `isBookmarked`
  為 false、收藏/取消收藏的 toggle、內容尚未載入時呼叫 `onToggleBookmark()` 是 no-op。

## Step 11：add saved articles screen with offline banner

- **`feature:saved`**：`SavedViewModel`（`BookmarkRepository.observeSaved()` + `NetworkMonitor` 的
  `combine`）、`SavedUiState`（`query` 欄位先放著但沒有 UI，等 Step 13）、`SavedScreen`（Empty/
  Loading/列表 三態、離線 banner、縮圖優先用 `localImagePath`）。底部導覽 Reading/Saved 完成
  （`app/LineFeedApp.kt` 把 Step 9 的 `SavedPlaceholderRoute` 換成真正的 `SavedRoute`/
  `savedScreen`），Saved → Detail 導覽接上。

- **模擬器手動驗證（本步驟做，涵蓋 Step 9 + 11 的清單）**：`~/Library/Android/sdk/emulator/emulator
  -avd Pixel_10_Pro_XL` 開機、`./gradlew :app:installDebug`、`adb shell am start`，實機截圖存在
  `/private/tmp/.../scratchpad/screens/`（未提交）。過程中抓到兩個先前步驟遺留、單元測試測不到的
  真實 bug：

  1. **`AndroidManifest.xml` 的 `android:name` 相對路徑錯誤（Step 1 遺留）**：
     `android:name=".LineFeedApplication"`／`".MainActivity"` 相對於 manifest 的
     `package`（等於 `namespace = "com.waynejiang.linefeed"`），解析成
     `com.waynejiang.linefeed.LineFeedApplication`；但類別實際在
     `com.waynejiang.linefeed.app` package 下，執行期
     `ClassNotFoundException` → app 開啟就閃退。**單元測試/Robolectric 都不會跑真正的
     manifest class 解析與啟動流程，所以 65+99+... 個單元測試全綠也完全沒發現這個問題**——
     這是本次唯一必須靠實機/模擬器才抓得到的一類 bug。修正為
     `android:name=".app.LineFeedApplication"`／`".app.MainActivity"`。
  2. **`FeedImage` 的 placeholder/error 判斷邏輯是假的（Step 8 遺留）**：原本用
     `rememberAsyncImagePainter` 讀 `.state` 決定要不要顯示 Icon，「顯示圖片」那個分支卻另外
     呼叫一個獨立的 `AsyncImage(model = model, ...)`——等於同一張圖發了兩個獨立請求：一個藏在
     `when` 判斷背後、從來沒有真的被排版（layout），Coil 拿不到目標尺寸所以回報
     `Empty`/`Error`；另一個才是真正顯示、有尺寸、會成功的請求。因為判斷式看的是「那個藏起來、
     注定失敗」的請求的狀態，畫面永遠卡在 placeholder icon，即使圖片其實已經下載成功
     （OkHttp log 顯示 200 OK）。Feed 列表卡片曾經「看起來正常」只是巧合（第一次截圖時機掩蓋了
     問題，後續在 Detail 頁用更大尺寸的 hero 圖重現才發現）。**改用 Coil3 官方支援的
     `SubcomposeAsyncImage(loading = {...}, error = {...})`**（單一請求、`loading`/`error` 是
     official 的 slot API），問題徹底消失。用 `Log.e` 暫時加在 `FeedImage` 裡印出
     `model`/`state` 才定位到「印一次就不再印」→ 該 composable 沒有隨真正的請求狀態重組——
     這個線索指向「讀狀態的物件」跟「顯示圖片的物件」根本是兩個不同的 Coil request。
  3. **`linefeed.db` 版本殘留問題**：模擬器上曾經因為先前失敗的啟動流程留下舊版本 schema 的
     DB 檔案（`Room` 丟 `A migration from 4 to 1 was required but not found`），`adb uninstall`
     重裝後消失——與程式碼本身無關，記錄是因為這是本機模擬器驗證的操作細節，不是要修的 bug。

  兩個修正後，實機驗證通過：冷啟動載入真實 API 資料（Spaceflight/Open-Meteo/DummyJSON）、
  weather hero 卡（含「Updated Xm ago」）、TopStory/ArticleRow/ServiceCard 正確穿插、收藏
  toggle 即時生效並反映在 Saved 頁、Saved → Detail 導覽、light/dark 兩種主題都清楚可讀（截圖
  比對）。滾動到底、離線飛航模式與下拉刷新的動畫時序未逐一截圖驗證，之後有機會再補；
  已驗證核心資料流與畫面切換沒有問題。

- **測試涵蓋**：`SavedViewModelTest`（loading→就緒轉換、空清單、清單反映 repository 內容、
  isOffline 跟隨 NetworkMonitor、移除收藏後清單即時更新）。

## Step 12：persist bookmark images for offline reading

- **`ImageDownloader`/`OkHttpImageDownloader`**（`core:data/network`）：下載到
  `<destination>.tmp`，整個 body 讀完才 `renameTo(destination)`，讀者永遠不會看到寫一半的檔案；
  process 死在下載中間只會留下孤兒 `.tmp`，不會壞掉「正式」檔案。
- **`DefaultBookmarkRepository`** 收藏時在 `@ApplicationScope` 啟動下載（不擋 `setBookmarked`
  呼叫端）；用 `ConcurrentHashMap<Long, Job>` 追蹤每篇文章目前的下載工作——取消收藏時先
  `cancel()` 對應的 Job 再刪 DB 列與本機檔案，避免「已經取消收藏，但背景下載晚一步完成又把
  `localImagePath` 寫回去」的競態；下載完成後也會重新查一次 `bookmarkDao.findById`，
  確認還在收藏清單裡才寫 `localImagePath`（雙重保險）。`retryPendingImageDownloads()` 真正實作：
  掃 `bookmarkDao.pendingImageDownloads()`（`localImagePath IS NULL AND imageUrl IS NOT NULL`）
  逐筆重試，接到 `AppRefreshInitializer`，在 `FOREGROUND`/`NETWORK_RESTORED` 觸發時呼叫。
- **`BookmarkDao` 新增 `findById`**（suspend，非 Flow）：取消收藏前要知道舊
  `localImagePath` 才能刪檔，`observeById` 是 Flow 不適合這種一次性讀取。
- **UI**：Step 10（Detail）與 Step 11（Saved）在寫的當下就已經預留
  `state.localImagePath?.let(::File) ?: article.imageUrl` 這個「本機優先」的邏輯，本步驟不用
  再改 UI。
- **測試涵蓋**：`DefaultBookmarkRepositoryTest` 新增：下載成功寫入真實檔案並可讀、下載失敗
  `localImagePath` 維持 null、沒有 `imageUrl` 的文章完全不觸發下載、取消收藏會刪除已下載的
  檔案、**取消收藏發生在下載仍在進行中時會取消該下載而不是與它賽跑**（用可控制的
  `CompletableDeferred` gate 模擬下載卡住的情境）、`retryPendingImageDownloads` 只對「缺本機
  圖片」的收藏重試、若全部都已有本機圖片則完全不會再打網路。
- **模擬器手動驗證（依 PLAN.md §10 Step 12 的驗收項目，做了簡化版）**：`Pixel_10_Pro_XL` 上
  收藏一篇文章 → `adb shell svc wifi disable && adb shell svc data disable`（比手動切飛航模式
  更可靠，模擬器的飛航模式 UI toggle 在此 Android 版本上不會真的斷網）→ 強制關閉並重開 App
  → **Saved 頁與 Reading 頁在完全離線情況下都正確顯示 offline banner，且已收藏文章的縮圖/大圖
  仍從本機檔案正確載入**（截圖存在 scratchpad，未提交）。沒有另外做「清除 App 儲存空間快取」
  這個子步驟（等同於清掉 OkHttp 磁碟快取但保留 app 私有檔案），因為 `bookmark_images` 本來就
  存在 `filesDir`（不是 `cacheDir`），架構上不會被「清除快取」動作影響，用停用網路已經足以
  驗證「不靠網路、不靠 HTTP 快取，純粹讀本機檔案」這件事。

## Step 13：add offline search over saved articles

- **`SavedViewModel`**：新增 `query: MutableStateFlow<String>`；`onQueryChange` 立即更新
  `query`（讓輸入框跟手），但實際打 `bookmarkRepository.observeSaved(q)` 的那條 flow 用
  `query.debounce { if (it.isBlank()) 0L else 300L }.flatMapLatest { observeSaved(it) }`——
  用「依值決定 debounce 時間」而不是固定 `debounce(300)`，是因為固定版本連冷啟動第一次的空字串
  查詢都會被延遲 300ms，導致 App 一開啟先空白閃一下才出現清單。
- **UI**：`SavedScreen` 頂部加 `OutlinedTextField` 搜尋欄（含清除按鈕），空清單依
  `query.isBlank()` 分兩種文案（「還沒有收藏」vs「找不到符合 "xxx" 的收藏」）；
  `SavedContent` 的 `LazyColumn` item 加 `Modifier.animateItem()`，取消收藏後其餘項目會平滑
  移動到新位置而不是瞬間跳動；`BookmarkIconButton` 的 crossfade 動畫在 Step 8 已經做好，
  這裡沿用。
- **問題（最花時間）**：`SavedViewModelTest` 要測「debounce 真的延遲了 300ms」，一開始沿用
  `MainDispatcherRule` 預設的 `UnconfinedTestDispatcher()`（它有自己獨立的
  `TestCoroutineScheduler`，跟 `runTest` 自己的 scheduler 是兩個不同的時鐘），導致
  `advanceTimeBy` 完全不影響 `debounce` 內部的 `delay()`。
  **解法**：在需要控制虛擬時間的兩個測試裡，改成
  `Dispatchers.setMain(StandardTestDispatcher(testScheduler))`——把 `Dispatchers.Main`
  換成跟 `runTest` 共用同一個 `testScheduler` 的 dispatcher，這樣 `advanceTimeBy`/
  `advanceUntilIdle`/`runCurrent` 才能真正推進 `viewModelScope` 裡的 `delay()`。
  **踩到的第二個坑**：這兩個測試一開始比照其他測試寫
  `try { ... } finally { Dispatchers.resetMain() }`，結果丟出
  `IllegalStateException: Dispatchers.Main was accessed when the platform dispatcher was
  absent and the test dispatcher was unset`。原因是 `ViewModel` 從來沒有人呼叫
  `onCleared()`（純 unit test 不会真的清掉 `viewModelScope`），`stateIn(WhileSubscribed(5000))`
  背後的協程仍然活著；`finally` 裡手動呼叫 `resetMain()` 的時機比 `runTest` 自己收尾時
  （會嘗試 flush/取消剩餘的子協程）還早，剩餘協程這時候想用 `Dispatchers.Main` 卻發現已經被
  reset 掉，就整個炸開。**解法**：這兩個測試乾脆不手動呼叫 `resetMain()`，讓外層
  `MainDispatcherRule` 的 `finished()`（在 `@Test` method 完全返回、`runTest` 自己的收尾都跑完
  之後才執行）統一處理，時機才對。
  **踩到的第三個坑**：改用「收集進一個 `List` 再看 `states.last()`」取代 Turbine 的
  `awaitItem()`——因為 `StateFlow` 一被訂閱就會先給出目前值（`SavedUiState()` 預設值），
  在 `StandardTestDispatcher` 下這個預設值與後續真正算出來的值是兩個分開、依序到達的項目，
  Turbine 嚴格「一次只能拿下一個」的語意跟這種「不確定會有幾個中間值」的情境不合拍；改看
  「目前為止收到的最後一個值」對中間到底發射幾次不敏感，斷言反而更準確也更好維護。
  **踩到的第四個坑（純粹是我自己測試資料設計錯誤）**：「快速輸入只會真的查一次」的測試一開始
  只收藏了一篇標題為「Rocket launch」的文章，导致「未過濾清單」與「用 "rocket" 過濾後的清單」
  剛好是同一個結果（因為就這一篇，且怎麼濾都符合），沒辦法分辨「有沒有中途多查了幾次」；
  補一篇不符合 "rocket" 的文章後才真的測到重點。
- **測試涵蓋**：`SavedViewModelTest` 新增：冷啟動空字串查詢不用等 debounce、
  `onQueryChange` 立即反映在 `query` 欄位但清單要等 debounce 結束才更新、快速輸入多次只有
  最後一個查詢字串真正打到 repository（中間值被跳過）。
- **模擬器驗證**：本步驟開始時模擬器已經在先前步驟驗證後關閉；重新檢查裝置列表時發現
  USB 上接的是一台真實實體手機（`ro.kernel.qemu`/`ro.boot.qemu` 皆為空，非模擬器），
  為了安全起見**沒有**對這台裝置執行任何 `install`/`shell` 修改類指令（只讀了
  `getprop` 確認它是實體機就停手），因此 Step 13 的搜尋 UI/動畫沒有額外補模擬器截圖，
  邏輯正確性由上述新增的 ViewModel 測試涵蓋。
