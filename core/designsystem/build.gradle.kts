plugins {
    id("linefeed.android.library")
    id("linefeed.android.compose")
}

android {
    namespace = "com.waynejiang.linefeed.core.designsystem"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)

    testImplementation(project(":core:testing"))
}
