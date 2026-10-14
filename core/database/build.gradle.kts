plugins {
    alias(libs.plugins.segue.android.library)
    alias(libs.plugins.segue.android.room)
    alias(libs.plugins.segue.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.paging.common)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
