package com.quantumff.segue.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

internal fun Project.configureAndroidCompose(extension: CommonExtension) {
    apply(plugin = "org.jetbrains.kotlin.plugin.compose")
    extension.buildFeatures.compose = true

    dependencies {
        val bom = platform(libs.library("androidx-compose-bom"))
        "implementation"(bom)
        "testImplementation"(bom)
        "androidTestImplementation"(bom)
        "implementation"(libs.library("androidx-compose-ui-tooling-preview"))
        "debugImplementation"(libs.library("androidx-compose-ui-tooling"))
    }

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        stabilityConfigurationFiles.add(
            isolated.rootProject.projectDirectory.file("compose_compiler_config.conf"),
        )
        // ./gradlew assembleRelease -Psegue.composeReports=true writes reports to build/compose-reports
        if (providers.gradleProperty("segue.composeReports").orNull == "true") {
            reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
            metricsDestination.set(layout.buildDirectory.dir("compose-reports"))
        }
    }
}
