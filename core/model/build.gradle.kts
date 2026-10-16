plugins {
    alias(libs.plugins.segue.jvm.library)
}

dependencies {
    // Domain models expose immutable collections so Compose treats them as stable.
    api(libs.kotlinx.collections.immutable)
}
