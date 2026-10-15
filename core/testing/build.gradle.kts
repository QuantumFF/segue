plugins {
    alias(libs.plugins.segue.android.library)
}

// Test helpers: the main-dispatcher rule, plus fake repositories for ViewModel tests.
dependencies {
    api(projects.core.data)
    api(projects.core.model)
    api(libs.androidx.paging.common)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.truth)
    api(libs.turbine)
}
