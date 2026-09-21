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
