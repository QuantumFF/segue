plugins {
    alias(libs.plugins.segue.android.feature)
    alias(libs.plugins.roborazzi)
}

// Screenshot goldens are committed. Record: ./gradlew :feature:nowplaying:recordRoborazziDebug
// Verify: ./gradlew :feature:nowplaying:verifyRoborazziDebug (plain test runs skip the comparison).
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    // Now Playing drives the player directly: transport, seek, modes, sleep timer.
    implementation(projects.core.playback)
    implementation(projects.core.common)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
