plugins {
    alias(libs.plugins.segue.jvm.library)
}

dependencies {
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.truth)
    api(libs.turbine)
}
