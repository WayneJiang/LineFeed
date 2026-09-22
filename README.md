# LineFeed：內容訂閱 App

Android 內容聚合應用，整合異質資訊流（文章、天氣、服務卡），支援離線閱讀與收藏。使用者可捲動瀏覽最新文章、收藏稍後詳讀，已收藏的內容及其附加圖片在離線時仍可存取。

## 快速開始

### 執行指令
```bash
# Build & 安裝
./gradlew installDebug

# 或僅 build
./gradlew assembleDebug

# 測試
./gradlew unitTest
```

### 環境需求
- **JDK**：21（Corretto 或同版本）
- **Android SDK**：最低 minSdk 24，targetSdk 36，compileSdk 37
- **無需 API key**：所有資料來源均免註冊

## 功能清單

- ✅ 完成 **Feed 分頁載入**（必做）：Spaceflight News API + Paging 3 RemoteMediator
- ✅ 完成 **詳情頁**（必做）：標題、摘要、來源、時間、作者、收藏按鈕
- ✅ 完成 **收藏/取消收藏（離線可讀）**（必做）：Room 快照 + 本機圖片永續化
- ✅ 完成 **已收藏清單頁**（必做）：本地搜尋過濾
- ✅ 完成 **異質 Feed（至少 2 種來源）**（必做）：天氣 hero 卡 (Open-Meteo) + 服務卡穿插 (DummyJSON)
- ✅ 完成 **新鮮度策略**（必做）：見下節詳述
- ✅ 完成 **所有 UI 狀態（載入/空/錯誤/離線）**（必做）：整頁與底部 footer 狀態完整
- ✅ 完成 **測試涵蓋快取與新鮮度邏輯**（加分）：170 測試全綠
- ✅ 完成 **多 module 結構**（加分）：8 module + build-logic
- ✅ 完成 **CI (GitHub Actions)**（加分）：push/PR 自動 build + test
- ✅ 完成 **Dark theme**（加分）：Material 3 light/dark
- ✅ 完成 **本地搜尋（Saved 頁）**（加分）：含防止 LIKE 萬用字元注入
- ✅ 完成 **多日天氣預報**（加分）：5 日後預報顯示

## 架構總覽

### Module 結構

```
app
├─ feature:feed（Reading 畫面：異質分頁 feed）
├─ feature:detail（詳情頁）
├─ feature:saved（已收藏清單：本地搜尋）
├─ core:domain（純 JVM，無 Android 依賴）
│  ├─ 領域模型與業務邏輯
│  ├─ Repository interface
│  └─ 新鮮度決策規則
├─ core:data（網路、資料庫、repo 實作）
├─ core:designsystem（theme、共用 UI）
└─ core:testing（Fake、MainDispatcherRule）
build-logic/convention
└─ 6 個 convention plugin（消除重複設定）
```

### 資料流

```
遠端 API（Spaceflight / Open-Meteo / DummyJSON）
    ↓
OkHttp 磁碟快取 (10 MB) + 尊重 Cache-Control
    ↓
Retrofit 3 + kotlinx.serialization
    ↓
Room（唯一資料來源）
    ├─ feed_articles（可拋棄的 feed 快取）
    ├─ remote_keys（Paging 3 游標）
    ├─ bookmarks（使用者收藏快照）
    ├─ weather_snapshots
    ├─ service_cards
    └─ sync_metadata（lastSuccessAt / lastError）
    ↓
ViewModel (StateFlow<UiState> + Flow<PagingData>)
    ↓
Compose UI（LazyColumn + LazyPagingItems）
```

## 新鮮度（Freshness）策略

### 核心原則
最大化資訊新鮮度，同時最小化行動網路流量消耗。**策略目標**：
1. 不做「不可能拿到新資料」的無謂請求（respect 來源更新頻率）
2. 刷新不造成圖片重新下載（保留快取、id 合併）
3. 使用者沒在看就不刷新（無背景同步）
4. 使用者明確要求時永遠尊重

### 定義「新鮮」

- **天氣** (Open-Meteo)
  - TTL (Wi-Fi / 行動網路)：15 分 / 30 分
  - Outdated 標示：3 小時
  - 理由：API 本身 15 分更新；即時性最高
- **文章** (Spaceflight, 第 1 頁)
  - TTL (Wi-Fi / 行動網路)：20 分 / 60 分
  - Outdated 標示：12 小時
  - 理由：約每小時 0.5 篇；API 宣告 max-age=600
- **服務卡** (DummyJSON)
  - TTL (Wi-Fi / 行動網路)：12 小時 / 24 小時
  - 理由：推廣內容以天為單位變動

**關鍵特性**：
- **Metered 網路 TTL 更長**（省流量），unmetered 更短（便宜，換新鮮度）
- **下一頁 (APPEND) 不受 TTL 管**：使用者主動向下捲即明確意圖
- **Stale-while-revalidate**：過期資料照常顯示，背景更新（無 loading spinner）
- **集中化決策**：`FreshnessPolicy` 是唯一的 TTL 判斷點，統一控制策略變更

### 觸發時機

- **冷啟動** (COLD_START)：顯示快取；逐來源判斷 TTL → 有過期則背景刷新
- **回前景** (FOREGROUND)：同上
- **網路復原** (NETWORK_RESTORED)：同上；Saved 頁回 Reading 頁時重試待機下載
- **下拉重整** (USER_PULL)：**忽略 TTL、全部刷新**（即使 metered）
- **背景中**：不刷新（無 WorkManager）

### 文章 Paging 3 整合

- **REFRESH**：拉第 1 頁 → 成功後在 transaction 內清空重建（`sortIndex` 從 0 重排）
- **APPEND**：保留 keyset 游標（`published_at_lte + id` 去重），查 DB 避免邊界重複，無新 id 時結束分頁
- **initialize()** 決策：新 Pager 建立時以 `FreshnessPolicy` 判斷「要不要拉第 1 頁」→ `LAUNCH_INITIAL_REFRESH` or `SKIP_INITIAL_REFRESH`

### 流量最佳化手段

- **Single-flight**：同一來源同時多觸發只打 1 次網路
- **文章刷新只拉第 1 頁**：APPEND 時舊頁內容讓使用者捲動才 lazy-load，圖片多數命中磁碟快取
- **看不到的畫面不刷新文章**：回前景時的刷新請求只在 Feed 可見時執行
- **離線不發 APPEND**：直接回 Error，UI 顯示離線 footer
- **無背景同步**：前景 SWR 足以保證「打開就新鮮」
- **DummyJSON 用 `select=`**：payload 減 60%

### 可測試性

- `AppClock` interface 注入系統時間（`FakeClock` 在測試中可任意進度）
- `FreshnessPolicy` 是純函式（JVM 單元測試）
- `RemoteMediator.initialize()` 注入 Fake，直接斷言觸發決策
- 所有時間用 `java.time` API（支援 minSdk 24 的 desugaring）

## Plan & Sequencing

### 工作拆解

逐步提交，每個 commit 可獨立 build 且測試全綠（完整歷史見 `git log`）：

- **Step 1** — `0511843` build: bootstrap gradle wrapper, version catalog and convention plugins\
  Gradle 9.7.1 wrapper、version catalog、`build-logic` 6 個 convention plugin、8 個 module 骨架、Hilt Application + MainActivity
- **Step 2** — `3f47897` ci: add github actions workflow for build and unit tests\
  GitHub Actions：push/PR 觸發 build + 單元測試
- **Step 3** — `1a9f4ff` feat(domain): add domain models, freshness policy and refresh triggers\
  `core:domain`：模型、`FreshnessPolicy`、`TtlConfig`、`AppClock`、`NetworkMonitor`、`SingleFlight`、`suspendRunCatching`、repository interface；`core:testing` 的 Fake
- **Step 4** — `7e5d97f` feat(data): add retrofit clients and dtos for spaceflight, open-meteo and dummyjson\
  三個 Retrofit API、DTO、RemoteDataSource、錯誤轉換；MockWebServer + JSON fixture 測解析
- **Step 5** — `27b6df8` feat(data): add room database for feed cache, bookmarks and sync metadata\
  Room entity/DAO（含 `sortIndex`、`remote_keys`、bookmarks、sync_metadata）、mapper、schema 匯出
- **Step 6** — `a77912f` feat(data): add paging 3 remote mediator with keyset append for articles\
  `ArticleRemoteMediator`：`initialize()` 走 FreshnessPolicy、REFRESH 清空重建、APPEND keyset 游標 + 去重
- **Step 7** — `e43b7c8` feat(data): add weather, service and bookmark repositories with refresh coordinator\
  天氣/服務卡/收藏 repository、`DefaultFeedRefresher` 刷新協調器、`ConnectivityNetworkMonitor`、回前景觸發接線
- **Step 8** — `d726498` feat(designsystem): add material 3 theme with dark mode and shared state components\
  Material 3 淺色/深色主題、共用元件（FullScreenMessage、OfflineBanner、FeedImage、SourceChip、Skeleton、`RelativeTimeFormatter`）
- **Step 9** — `29ee21f` feat(feed): add paged heterogeneous feed with weather hero and service card separators\
  Reading 頁：天氣 hero、首篇大圖卡、文章列、服務卡穿插、下拉重整、分頁 footer、Coil 設定、底部導覽；模擬器驗證時抓到並修正 Manifest 路徑與 FeedImage 兩個 bug
- **Step 10** — `bc7da12` feat(detail): add article detail screen with bookmark toggle\
  詳情頁、收藏切換、Feed → Detail 導覽
- **Step 11** — `d548a7a` feat(saved): add saved articles screen with offline banner\
  Saved 頁、離線 banner、Saved → Detail 導覽
- **Step 12** — `c0fc876` feat(data): persist bookmark images for offline reading\
  收藏時把圖片下載到 `filesDir`（tmp → rename）、取消收藏刪檔、失敗重試；UI 優先讀本機圖片
- **Step 13** — `ff56dc9` feat(saved): add offline search over saved articles\
  Saved 頁本機搜尋（debounce、LIKE 萬用字元跳脫、無結果空狀態）+ 列表小動畫

**計畫外的後續 commit**：
- `e475bdd` feat(feed): show multi-day forecast row in weather hero card（主控看截圖 review 發現天氣卡缺多日預報後補上）

### 為什麼這個順序？

根據 PLAN.md §10 開頭的四點理由：

1. **先穩定 build 與 CI**（Step 1-2）：後續每個 commit 都被 GitHub Actions 驗證 → 早期發現問題
2. **由內而外：domain → data → feature**：依賴方向自動強制；feature 無法繞過 data 層直接用 Room
3. **must-have 與架構優先於 nice-to-have**：異質 feed 與離線機制是核心，搜尋/動畫其次
4. **每個 commit 都可獨立 build 且測試全綠**：減少跨 commit 的隱藏依賴

### PLAN v1 → v2 差異

- **分頁機制**
  - v1（手寫 keyset 分頁）：自寫 `loadNextPage()`；VM 持有 `AppendState`（Idle/Loading/Error/EndReached/Offline）狀態機；`snapshotFlow` 偵測接近底部觸發
  - v2（Paging 3 + RemoteMediator）：Paging 3 內建 append 觸發、prefetch、`LoadState`、`retry()`
- **下一頁游標**
  - v1（手寫 keyset 分頁）：`published_at_lte` + id 去重
  - v2（Paging 3 + RemoteMediator）：保留不變，移到 RemoteMediator 的 APPEND；游標存在 `remote_keys` 表
- **異質混排**
  - v1（手寫 keyset 分頁）：純函式 `FeedAssembler` 把天氣/文章/服務卡組成一個 List
  - v2（Paging 3 + RemoteMediator）：天氣 hero 是 LazyColumn 的獨立 `item {}`（不進 PagingData）；服務卡用 `insertSeparators` 依 `sortIndex` 規則穿插（`ServiceCardSlots`）
- **Room schema**
  - v1（手寫 keyset 分頁）：`feed_articles`
  - v2（Paging 3 + RemoteMediator）：`feed_articles` 加遞增 `sortIndex`（unique index）+ 新增 `remote_keys` 表（每個 feed 一列）
- **新鮮度整合**
  - v1（手寫 keyset 分頁）：刷新協調器統一決定所有來源
  - v2（Paging 3 + RemoteMediator）：同一個 `FreshnessPolicy`，但文章冷啟動改由 `RemoteMediator.initialize()` 判斷 SKIP/LAUNCH；回前景時協調器發出刷新請求，Feed 可見時才呼叫 `refresh()`
- **文章刷新策略**
  - v1（手寫 keyset 分頁）：「有重疊就合併」保留已載入舊頁
  - v2（Paging 3 + RemoteMediator）：REFRESH 成功後在 transaction 內清空重建、`sortIndex` 從 0 重排；「保留舊頁」移到延後清單
- **ViewModel 狀態**
  - v1（手寫 keyset 分頁）：單一 `StateFlow<UiState>`
  - v2（Paging 3 + RemoteMediator）：非分頁狀態仍是 `StateFlow<FeedUiState>`；文章+服務卡另以 `Flow<PagingData<FeedItem>>` 暴露
- **UI 狀態推導**
  - v1（手寫 keyset 分頁）：`deriveFullScreenState` 從 AppendState 推導
  - v2（Paging 3 + RemoteMediator）：純函式 `deriveFeedScreenState(CombinedLoadStates, itemCount, isOffline)`
- **測試**
  - v1（手寫 keyset 分頁）：手寫分頁狀態機、`FeedAssemblerTest`
  - v2（Paging 3 + RemoteMediator）：`ArticleRemoteMediatorTest`、PagingSource 用 `TestPager`、VM/Repository 用 `asSnapshot()`、`ServiceCardSlotsTest`、`DeriveFeedScreenStateTest`
- **Commit 計畫**
  - v1（手寫 keyset 分頁）：Step 6 `implement offline-first repositories with keyset pagination`、Step 7 `refresh coordinator and network monitor`
  - v2（Paging 3 + RemoteMediator）：Step 6 `add paging 3 remote mediator with keyset append`、Step 7 `weather, service and bookmark repositories with refresh coordinator`；Step 1–5 不變

v1 不採用 Paging 3 的完整理由與推翻過程見 DECISIONS.md §8；v2 修訂紀錄見 docs/PLAN.md 開頭。

### 砍掉的項目

- **電影卡 (TMDB)**：需要 API key → 面試官 clone 不能直接跑
- **背景定期同步**：前景 SWR 已足；背景同步浪費流量 & 電量
- **定位的天氣**：定位權限流程與新鮮度主題無關
- **遠端搜尋**：選本地過濾（離線可用）
- **Compose UI 測試**：UI 狀態由 ViewModel 單元測試覆蓋
- **漢堡選單**：無實際內容可放

## AI 開發流程

```
Wayne (Human)
 ├─ 指定流程與模型分工
 ├─ 質疑「為什麼不用 Paging 3」→ 決定改用（PLAN v2）
 │
 ├→ Opus 5 (規劃)
 │  └─ PLAN.md v1 + v2 (commit 18a12ad, b4a338e)
 │
 ├→ Sonnet 5 (實作)
 │  └─ Step 1-13 + 多日預報補強 (e475bdd)
 │     每步一個 commit
 │     Step 9 模擬器驗證發現 Manifest/FeedImage bug
 │
 ├→ Claude Code / Opus 5 (主控)
 │  └─ 派工、驗證每步 build/test
 │     獨立重跑驗證
 │     看截圖 review、回答 Wayne 的 Paging 3 追問
 │
 └→ Haiku 4.5 (文件)
    └─ 讀 PDF、寫 README/DECISIONS/AI_USAGE
       初稿有捏造內容，主控查證後修正
```

### Commit 中的 Role 標示

每個 commit body 都標有 `Role:`，說明作者身份：
- `Role: planning by Opus` — 架構與計畫
- `Role: implementation and unit tests by Sonnet` — 功能 + 測試
- `Role: revision by Opus after review by the human author` — 決策改動
- `Role: gap found in review by the orchestrator` — 主控 review 發現

## 測試

### 統計

- **總計**：170 unit tests（全綠 ✅）
- **分佈**：
  - `core:domain` — 26 tests
  - `core:data` — 80 tests
  - `core:designsystem` — 8 tests
  - `feature:feed` — 39 tests
  - `feature:detail` — 8 tests
  - `feature:saved` — 9 tests
- **達成時機**：Step 1-5 結束時 65 tests；Step 6-13 補至 170

### 測試策略重點

- **Domain**
  - 策略：純 JVM + 純函式測試
  - 理由：Freshness、Trigger 無副作用 → 快速驗證
- **DAO**
  - 策略：Robolectric (SDK 35) + in-memory Room
  - 理由：測 Paging + bookmark invalidation
- **RemoteMediator**
  - 策略：Robolectric + FakeClock + FakeNetworkMonitor
  - 理由：驗證 initialize()/REFRESH/APPEND 決策
- **Repository**
  - 策略：Fake remote + 真實 Room
  - 理由：測新鮮度決策、邊界條件
- **ViewModel**
  - 策略：Fake repo + StateFlow 驗證
  - 理由：測狀態機與生命週期
- **Paging 轉換**
  - 策略：純函式 + `flowOf()` + `asSnapshot()`
  - 理由：Separator 插入、key 唯一性

### 為何 SDK 35 而非 36？

NOTES.md Step 5：Robolectric 4.17 的 SDK 36 shadow 在 JDK 21 下 `ApplicationSharedMemory` 反射失敗；改用 SDK 35（仍在支援與 minSdk..targetSdk 範圍內）完全通過。環境特異性問題，已驗證。

### 未做的項目

- Compose UI 截圖測試（UI 狀態由 ViewModel 單元測試覆蓋）
- 搜尋 UI 與 animateItem() 動畫未有裝置截圖驗證（單元測試涵蓋邏輯）

## 已知限制與未來方向

### 資料內容限制

- **詳情頁無完整內文**：Spaceflight API 只提供 `summary`
- **文章列表無遠端搜尋**：只實作本地過濾（離線可用）
- **縮圖尺寸單一**：Spaceflight 每篇圖只有一個尺寸
- **相對時間用 US Locale**：便於單元測試斷言格式

### UI 與互動限制

- **無 Undo 提示**：好的 UX，但非必要
- **天氣無定位**：固定台北；定位權限流程與新鮮度主題無關
- **服務卡穿插規則固定**：依 `ServiceCardSlots(firstAfter=3, every=6)` 決定插入位置

### 架構延後

- **拆分 core:network / core:database**：目前只有 1 個消費者（core:data）；等第 2 個消費者出現再拆（YAGNI）
- **DataStore metadata**：目前 Room transaction 已保證 TTL 原子性；若需要多 DB 才考慮

### 單元測試未涵蓋的情景

- **實體手機端對端流程**（本作只用模擬器驗證核心資料流、導覽、offline/online 切換）
- **Rate limit 與 API 不穩定情況**（mock fixture 無法重現；需要正式環境測試）
- **極端網路延遲**（測試用 Fake 與 MockWebServer，模擬不出真實 4G 抖動）

## 技術棧總覽

- **語言**：Kotlin（2.4.20）
- **Build**：Gradle（9.7.1）
- **AGP**：9.4.0
- **UI**：Jetpack Compose + Material 3（2026.09.00 BOM）
- **狀態**：StateFlow + UDF
- **DI**：Hilt + KSP（2.60.1 / 2.3.12）
- **資料庫**：Room（2.8.5）
- **網路**：Retrofit 3 + OkHttp 5 + kotlinx.serialization（3.0.0 / 5.5.0 / 1.11.0）
- **分頁**：Paging 3 + RemoteMediator（3.5.1）
- **圖片**：Coil 3（3.6.2）
- **並行**：Coroutines + Flow（1.11.0）
- **測試**：JUnit 4 + Turbine + Robolectric + paging-testing（4.13.2 / 1.2.1 / 4.17 / 3.5.1）
- **CI**：GitHub Actions

## 資料來源

新鮮度相關的 TTL、payload 大小、API 更新頻率數據來自 PLAN.md §3.1 的規劃期實測。
