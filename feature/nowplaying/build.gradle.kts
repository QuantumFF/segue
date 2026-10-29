plugins {
    alias(libs.plugins.segue.android.feature)
}

dependencies {
    // Now Playing drives the player directly: transport, seek, modes, sleep timer.
    implementation(projects.core.playback)
    implementation(projects.core.common)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
