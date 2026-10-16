plugins {
    alias(libs.plugins.segue.android.library)
    alias(libs.plugins.segue.android.room)
    alias(libs.plugins.segue.hilt)
    alias(libs.plugins.kotlin.serialization)
}

androidComponents {
    // MigrationTestHelper reads the exported schemas from the unit test assets.
    onVariants { variant ->
        variant.hostTests.values.forEach { it.sources.assets?.addStaticSourceDirectory("$projectDir/schemas") }
    }
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.paging.common)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
