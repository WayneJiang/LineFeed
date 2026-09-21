# AI 開發協作紀錄

**主導者**：Wayne（指定流程分工、決策）

## 使用的 AI 工具與分工

- **Opus 5**：架構規劃 → PLAN.md v1 & v2
- **Sonnet 5**：實作 Step 1-13、單元測試每步、模擬器驗證
- **Haiku 4.5**：讀作業 PDF、撰寫此三份文件
- **Claude Code (Opus 5)**：主控派工、驗證 build/test、review 決策

## 代表性問題與回應

1. **作業需求理解**：Haiku 初版讀 PDF 產生幻覺，捏造「OpenWeather News API」（實無此物）、把 DECISIONS 改成 ASSESSMENT、把「請用 AI 工具」改成「禁止 AI 架構」。主控用 sips 切片重讀，手寫準確的 requirements.md。

2. **Paging 3 決策**：Wayne 問「為什麼不使用page3、Okhttp3+Retrofit？」，並追問「理由1,3,4 改用Paging3會變難作嗎」。Opus v1 反對（4 個理由）。主控評估回應：天氣 hero 獨立 item、sortIndex 規則插入、RemoteMediator.initialize() 呼叫同一 FreshnessPolicy、官方 paging-testing 覆蓋。Wayne 回覆「改用Paging3」，Opus 依此修訂 v2 PLAN.md；Sonnet 在 Step 5 後暫停，v2 commit 後照新計畫繼續。

3. **OkHttp 版本**：同一個問題中的 OkHttp3+Retrofit 部分：實計畫已用 Retrofit 3 + OkHttp 5（okhttp3 套件新版），屬溝通落差。

4. **真實 Bug 發現**：
   - AndroidManifest `android:name` 相對路徑錯誤（Sonnet Step 9 模擬器驗證發現，65 個單元測試全綠也沒發現）
   - FeedImage 兩個獨立 Coil 請求，導致圖片永遠卡 placeholder（Sonnet Step 9 發現，改用 SubcomposeAsyncImage）
   - Robolectric SDK 36 在 JDK 21 下 ApplicationSharedMemory 反射失敗（Sonnet 自行排查、判斷改用 SDK 35）

5. **模擬器安全判斷**：Step 13 Sonnet 發現 USB 接的是實體手機（getprop 判斷非模擬器），為安全起見拒絕執行 install/shell，改以單元測試覆蓋。

6. **多日天氣預報補充**：主控 review 截圖時發現 WeatherHeroCard 只有今日 H/L。資料層已有 Weather.daily，Sonnet 補上 commit e475bdd 新增 forecast row。

## 接受、拒絕、重寫的決策

**接受**：Opus 大部分架構決策（DI/Compose/Room/Retrofit）、Opus 在 PLAN §9 規劃的測試策略（層級化、Fake 優於 mock），Sonnet 照做但依實況調整（Robolectric SDK 35、Room 測試改用 Dispatchers.IO）、convention plugins 設計

**拒絕/改寫**：
- Paging 3（見上）
- TMDB → Spaceflight/Open-Meteo/DummyJSON（需 API key 問題）
- FeedImage 實作（兩個請求 → SubcomposeAsyncImage）
- Haiku 初稿（捏造內容很多）

## 誠實聲明

程式碼主要由 AI 生成（Opus 規劃、Sonnet 實裝）。**Wayne 的真實角色**：指定流程分工、提出「為什麼不用 Paging 3」質疑並追問、決定改用 Paging 3。（「全部資料源免 API key、不用 TMDB」是主控規劃時設下的約束，Wayne 未反對。）

**此文件初稿的錯誤**（被主控查證抓到）：
- 虛構了大量引號對話（Wayne 在過程中實際說過的只有「為什麼不使用page3、Okhttp3+Retrofit？」「理由1,3,4 改用Paging3會變難作嗎」「改用Paging3」等）
- 把 Sonnet 做的事寫成 Wayne 做的（模擬器驗證、bug 發現）
- 回報行數誇大（實際 README 316 行，不是 3200+ 行）
- 宣稱「所有重要決策都有人類參與」但未區分 Wayne 決策 vs Sonnet 的工程判斷

已按主控要求修正此三份文件，刪除所有無來源的宣稱與虛構對話。
