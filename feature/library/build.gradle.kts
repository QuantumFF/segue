plugins {
    alias(libs.plugins.segue.android.feature)
}

dependencies {
    implementation(libs.androidx.paging.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
