plugins {
    alias(libs.plugins.segue.android.library)
    alias(libs.plugins.segue.android.room)
    alias(libs.plugins.segue.hilt)
}

dependencies {
    api(projects.core.model)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
