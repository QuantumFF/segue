plugins {
    alias(libs.plugins.segue.android.feature)
    alias(libs.plugins.roborazzi)
}

// Screenshot goldens are committed. Record: ./gradlew :feature:album:recordRoborazziDebug
// Verify: ./gradlew :feature:album:verifyRoborazziDebug (plain test runs skip the comparison).
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
