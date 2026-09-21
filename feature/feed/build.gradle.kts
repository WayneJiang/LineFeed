plugins {
    id("linefeed.android.feature")
}

android {
    namespace = "com.waynejiang.linefeed.feature.feed"
}

dependencies {
    implementation(libs.paging.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)

    testImplementation(libs.paging.testing)
}
