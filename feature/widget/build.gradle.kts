plugins {
    alias(libs.plugins.segue.android.feature)
}

// Home-screen widgets (Jetpack Glance). Updated by the playback service on player events only.
dependencies {
    implementation(projects.core.common)
    implementation(projects.core.playback)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
