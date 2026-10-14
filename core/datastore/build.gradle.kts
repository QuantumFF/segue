plugins {
    alias(libs.plugins.segue.android.library)
    alias(libs.plugins.segue.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
}
