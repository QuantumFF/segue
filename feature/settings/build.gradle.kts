plugins {
    alias(libs.plugins.segue.android.feature)
}

dependencies {
    // Asks for the Nearby devices permission "Play when Bluetooth connects" needs.
    implementation(libs.androidx.activity.compose)
}
