plugins {
    alias(libs.plugins.segue.android.library)
}

// Test helpers: the main-dispatcher rule, fake repositories for ViewModel tests, and Compose
// semantics matchers. Compose UI tests bring their own ui-test dependency, so it is compile-only here.
dependencies {
    api(projects.core.data)
    api(projects.core.model)
    api(libs.androidx.paging.common)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.truth)
    api(libs.turbine)
    compileOnly(platform(libs.androidx.compose.bom))
    compileOnly(libs.androidx.compose.ui.test)
}
