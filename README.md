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

| 功能 | 必做/加分 | 狀態 | 備註 |
|---|---|---|---|
| Feed 分頁載入 | 必做 | ✅ 完成 | Spaceflight News API + Paging 3 RemoteMediator |
| 詳情頁 | 必做 | ✅ 完成 | 標題、摘要、來源、時間、作者、收藏按鈕 |
| 收藏/取消收藏（離線可讀） | 必做 | ✅ 完成 | Room 快照 + 本機圖片永續化 |
| 已收藏清單頁 | 必做 | ✅ 完成 | 本地搜尋過濾 |
| 異質 Feed（至少 2 種來源） | 必做 | ✅ 完成 | 天氣 hero 卡 (Open-Meteo) + 服務卡穿插 (DummyJSON) |
| 新鮮度策略 | 必做 | ✅ 完成 | 見下節詳述 |
| 所有 UI 狀態（載入/空/錯誤/離線） | 必做 | ✅ 完成 | 整頁與底部 footer 狀態完整 |
| 測試涵蓋快取與新鮮度邏輯 | 加分 | ✅ 完成 | 170 測試全綠 |
| 多 module 結構 | 加分 | ✅ 完成 | 8 module + build-logic |
| CI (GitHub Actions) | 加分 | ✅ 完成 | push/PR 自動 build + test |
| Dark theme | 加分 | ✅ 完成 | Material 3 light/dark |
| 本地搜尋（Saved 頁） | 加分 | ✅ 完成 | 含防止 LIKE 萬用字元注入 |
| 多日天氣預報 | 加分 | ✅ 完成 | 5 日後預報顯示 |

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

| 來源 | TTL (Wi-Fi / 行動網路) | Outdated 標示 | 理由 |
|---|---|---|---|
| **天氣** (Open-Meteo) | 15 分 / 30 分 | 3 小時 | API 本身 15 分更新；即時性最高 |
| **文章** (Spaceflight, 第 1 頁) | 20 分 / 60 分 | 12 小時 | 約每小時 0.5 篇；API 宣告 max-age=600 |
| **服務卡** (DummyJSON) | 12 小時 / 24 小時 | — | 推廣內容以天為單位變動 |

**關鍵特性**：
- **Metered 網路 TTL 更長**（省流量），unmetered 更短（便宜，換新鮮度）
- **下一頁 (APPEND) 不受 TTL 管**：使用者主動向下捲即明確意圖
- **Stale-while-revalidate**：過期資料照常顯示，背景更新（無 loading spinner）
- **集中化決策**：`FreshnessPolicy` 是唯一的 TTL 判斷點，統一控制策略變更

### 觸發時機

| 情景 | 行為 |
|---|---|
| **冷啟動** (COLD_START) | 顯示快取；逐來源判斷 TTL → 有過期則背景刷新 |
| **回前景** (FOREGROUND) | 同上 |
| **網路復原** (NETWORK_RESTORED) | 同上；Saved 頁回 Reading 頁時重試待機下載 |
| **下拉重整** (USER_PULL) | **忽略 TTL、全部刷新**（即使 metered） |
| **背景中** | 不刷新（無 WorkManager） |

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

16 個 commit：

**Step 1-2（基礎建設）**：bootstrap gradle wrapper & convention plugins、GitHub Actions CI

**Step 3-7（資料層）**：core:domain models → Retrofit/Room entities/DAO → RemoteMediator → repositories & DI

**Step 8-11（UI 層）**：Material 3 theme → Feed 異質分頁 → Detail → Saved + 離線 banner

**Step 12-13（補充）**：收藏圖片離線保存 → Saved 頁離線搜尋（debounce）

**Step 14（文件）**：此三份檔案

**另**：Step 9 模擬器驗證發現 Manifest/FeedImage bug 並修正；主控 review 截圖時發現多日天氣預報缺口，派 Sonnet 補上 commit e475bdd

### 為什麼這個順序？

根據 PLAN.md §10 開頭的四點理由：

1. **先穩定 build 與 CI**（Step 1-2）：後續每個 commit 都被 GitHub Actions 驗證 → 早期發現問題
2. **由內而外：domain → data → feature**：依賴方向自動強制；feature 無法繞過 data 層直接用 Room
3. **must-have 與架構優先於 nice-to-have**：異質 feed 與離線機制是核心，搜尋/動畫其次
4. **每個 commit 都可獨立 build 且測試全綠**：減少跨 commit 的隱藏依賴

### 中途架構變更（Paging 3）

初版計畫（Opus v1）採手寫 keyset 分頁，理由四點。Wayne 質疑「為什麼不用 Paging 3」，主控評估各理由並提出回應：

- 天氣 hero 不必進 `PagingData`、服務卡用 `sortIndex` 規則插入
- `initialize()` 直接呼叫 `FreshnessPolicy`，決策點仍唯一
- Paging 內建 LoadState/retry/append 觸發（省工）
- 官方 `paging-testing` 覆蓋可測性

Wayne 同意改用 Paging 3。Opus 修訂 v2 PLAN.md，Sonnet 在 Step 5 後暫停、awaiting v2 commit，隨後照新計畫繼續 Step 6-13。

### 砍掉的項目

| 項目 | 理由 |
|---|---|
| **電影卡 (TMDB)** | 需要 API key → 面試官 clone 不能直接跑 |
| **背景定期同步** | 前景 SWR 已足；背景同步浪費流量 & 電量 |
| **定位的天氣** | 定位權限流程與新鮮度主題無關 |
| **遠端搜尋** | 選本地過濾（離線可用） |
| **Compose UI 測試** | UI 狀態由 ViewModel 單元測試覆蓋 |
| **漢堡選單** | 無實際內容可放 |

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

| 層級 | 策略 | 理由 |
|---|---|---|
| **Domain** | 純 JVM + 純函式測試 | Freshness、Trigger 無副作用 → 快速驗證 |
| **DAO** | Robolectric (SDK 35) + in-memory Room | 測 Paging + bookmark invalidation |
| **RemoteMediator** | Robolectric + FakeClock + FakeNetworkMonitor | 驗證 initialize()/REFRESH/APPEND 決策 |
| **Repository** | Fake remote + 真實 Room | 測新鮮度決策、邊界條件 |
| **ViewModel** | Fake repo + StateFlow 驗證 | 測狀態機與生命週期 |
| **Paging 轉換** | 純函式 + `flowOf()` + `asSnapshot()` | Separator 插入、key 唯一性 |

### 為何 SDK 35 而非 36？

NOTES.md Step 5：Robolectric 4.17 的 SDK 36 shadow 在 JDK 21 下 `ApplicationSharedMemory` 反射失敗；改用 SDK 35（仍在支援與 minSdk..targetSdk 範圍內）完全通過。環境特異性問題，已驗證。

### 未做的項目

- Compose UI 截圖測試（UI 狀態由 ViewModel 單元測試覆蓋）
- 搜尋 UI 與 animateItem() 動畫未有裝置截圖驗證（單元測試涵蓋邏輯）

## 已知限制與未來方向

### 資料內容限制

| 限制 | 原因 |
|---|---|
| **詳情頁無完整內文** | Spaceflight API 只提供 `summary` |
| **文章列表無遠端搜尋** | 只實作本地過濾（離線可用） |
| **縮圖尺寸單一** | Spaceflight 每篇圖只有一個尺寸 |
| **相對時間用 US Locale** | 便於單元測試斷言格式 |

### UI 與互動限制

| 限制 | 理由 |
|---|---|
| **無 Undo 提示** | 好的 UX，但非必要 |
| **天氣無定位** | 固定台北；定位權限流程與新鮮度主題無關 |
| **服務卡穿插規則固定** | 依 `ServiceCardSlots(firstAfter=3, every=6)` 決定插入位置 |

### 架構延後

| 項目 | 何時適合 |
|---|---|
| **拆分 core:network / core:database** | 目前只有 1 個消費者（core:data）；等第 2 個消費者出現再拆（YAGNI） |
| **DataStore metadata** | 目前 Room transaction 已保證 TTL 原子性；若需要多 DB 才考慮 |

### 單元測試未涵蓋的情景

- **實體手機端對端流程**（本作只用模擬器驗證核心資料流、導覽、offline/online 切換）
- **Rate limit 與 API 不穩定情況**（mock fixture 無法重現；需要正式環境測試）
- **極端網路延遲**（測試用 Fake 與 MockWebServer，模擬不出真實 4G 抖動）

## 技術棧總覽

| 範疇 | 技術 | 版本 |
|---|---|---|
| **語言** | Kotlin | 2.4.20 |
| **Build** | Gradle | 9.7.1 |
| **AGP** | | 9.4.0 |
| **UI** | Jetpack Compose + Material 3 | 2026.09.00 BOM |
| **狀態** | StateFlow + UDF | — |
| **DI** | Hilt + KSP | 2.60.1 / 2.3.12 |
| **資料庫** | Room | 2.8.5 |
| **網路** | Retrofit 3 + OkHttp 5 + kotlinx.serialization | 3.0.0 / 5.5.0 / 1.11.0 |
| **分頁** | Paging 3 + RemoteMediator | 3.5.1 |
| **圖片** | Coil 3 | 3.6.2 |
| **並行** | Coroutines + Flow | 1.11.0 |
| **測試** | JUnit 4 + Turbine + Robolectric + paging-testing | 4.13.2 / 1.2.1 / 4.17 / 3.5.1 |
| **CI** | GitHub Actions | — |

## 資料來源

新鮮度相關的 TTL、payload 大小、API 更新頻率數據來自 PLAN.md §3.1 的規劃期實測。
