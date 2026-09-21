plugins {
    id("linefeed.jvm.library")
}

// Fakes live in `main` (not `test`) so every other module can depend on them via
// `testImplementation(project(":core:testing"))`. `api` is used here (not `implementation`)
// because these types (AppClock, NetworkMonitor, ...) are meant to leak into consumers' test code.
dependencies {
    api(project(":core:domain"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
