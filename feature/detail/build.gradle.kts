plugins {
    id("linefeed.android.feature")
}

android {
    namespace = "com.waynejiang.linefeed.feature.detail"
}

dependencies {
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons.extended)
}
