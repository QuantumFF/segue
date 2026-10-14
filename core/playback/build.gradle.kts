plugins {
    alias(libs.plugins.segue.android.library)
    alias(libs.plugins.segue.hilt)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.data)

    api(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    // The app's shared OkHttpClient (provided by core:network) streams audio.
    implementation(libs.okhttp.core)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
