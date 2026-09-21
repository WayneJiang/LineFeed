plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}

// Root aggregate task so `./gradlew unitTest` runs every module's unit tests with one command.
// Uses string task paths (not cross-project `project(...)` references) so this stays
// configuration-cache safe.
tasks.register("unitTest") {
    group = "verification"
    description = "Runs unit tests for all modules."
    dependsOn(
        ":core:domain:test",
        ":core:testing:test",
        ":core:data:testDebugUnitTest",
        ":core:designsystem:testDebugUnitTest",
        ":feature:feed:testDebugUnitTest",
        ":feature:detail:testDebugUnitTest",
        ":feature:saved:testDebugUnitTest",
        ":app:testDebugUnitTest",
    )
}
