plugins {
    alias(libs.plugins.segue.android.library)
    alias(libs.plugins.segue.hilt)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
