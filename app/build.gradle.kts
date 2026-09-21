plugins {
    id("linefeed.android.application")
    id("linefeed.android.compose")
    id("linefeed.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.waynejiang.linefeed"

    defaultConfig {
        applicationId = "com.waynejiang.linefeed"
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:feed"))
    implementation(project(":feature:detail"))
    implementation(project(":feature:saved"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(project(":core:testing"))
}
