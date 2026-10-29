plugins {
    alias(libs.plugins.segue.android.feature)
}

dependencies {
    implementation(projects.core.playback)
    // Drag-to-reorder in LazyColumn (Apache-2.0).
    implementation(libs.reorderable)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
