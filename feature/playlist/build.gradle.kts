plugins {
    alias(libs.plugins.segue.android.feature)
}

dependencies {
    implementation(projects.core.common)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
