plugins {
    alias(libs.plugins.segue.android.feature)
}

dependencies {
    // Asks for the Nearby devices permission "Play when Bluetooth connects" needs.
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
