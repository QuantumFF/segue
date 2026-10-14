plugins {
    alias(libs.plugins.segue.compose.application)
    alias(libs.plugins.segue.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.quantumff.segue"

    defaultConfig {
        applicationId = "com.quantumff.segue"
        versionCode = 1
        versionName = "0.1.0"
    }

    // Release signing comes from SEGUE_* environment variables (set by .github/workflows/release.yml).
    // Without them, release builds are signed with the debug key, so local builds need no keystore.
    val releaseKeystore = providers.environmentVariable("SEGUE_KEYSTORE_FILE")
    if (releaseKeystore.isPresent) {
        signingConfigs.create("release") {
            storeFile = file(releaseKeystore.get())
            storePassword = providers.environmentVariable("SEGUE_KEYSTORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("SEGUE_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("SEGUE_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // No Google-encrypted dependency metadata blob in the APK (keeps F-Droid builds reproducible).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

baselineProfile {
    // Generate on demand with :app:generateBaselineProfile, not on every release build.
    automaticGenerationDuringBuild = false
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.playback)

    implementation(projects.feature.album)
    implementation(projects.feature.artist)
    implementation(projects.feature.downloads)
    implementation(projects.feature.home)
    implementation(projects.feature.library)
    implementation(projects.feature.login)
    implementation(projects.feature.nowplaying)
    implementation(projects.feature.playlist)
    implementation(projects.feature.queue)
    implementation(projects.feature.search)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.hilt.compiler)

    baselineProfile(projects.baselineprofile)
}
