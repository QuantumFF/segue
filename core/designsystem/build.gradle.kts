plugins {
    alias(libs.plugins.segue.compose.library)
    alias(libs.plugins.roborazzi)
}

// Screenshot goldens are committed. Record: ./gradlew :core:designsystem:recordRoborazziDebug
// Verify: ./gradlew :core:designsystem:verifyRoborazziDebug (plain test runs skip the comparison).
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.palette)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
