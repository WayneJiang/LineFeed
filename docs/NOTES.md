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

## 一般記錄
- 本機環境確認：JDK 21 (Corretto)、Android SDK 已有 platforms 35/36/37.0、
  `~/.gradle/wrapper/dists` 已有 gradle-9.7.1-bin 快取、AGP 9.4.0 jar 已在
  `~/.gradle/caches/modules-2` 中（與 PLAN §8.1 的驗證證據一致）。
- `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar` 從
  `~/Github/MyPoke`（同版本 9.7.1）複製後改寫 `gradle-wrapper.properties`，
  未使用官方 `gradle wrapper` 指令重新產生（效果相同，皆為 9.7.1 bin distribution）。
