plugins {
    alias(libs.plugins.segue.android.feature)
}

dependencies {
    implementation(projects.core.playback)
    // Drag-to-reorder in LazyColumn (Apache-2.0).
    implementation(libs.reorderable)
}
