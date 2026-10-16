plugins {
    alias(libs.plugins.segue.android.feature)
    alias(libs.plugins.roborazzi)
}

// Screenshot goldens: record with recordRoborazziDebug, check with verifyRoborazziDebug.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
