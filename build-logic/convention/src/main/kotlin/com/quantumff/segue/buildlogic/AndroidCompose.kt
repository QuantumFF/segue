package com.quantumff.segue.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

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
    }

    // Debug compilations only, opt-in: ./gradlew assembleDebug -Psegue.composeReports=true writes
    // stability reports and metrics to <module>/build/compose-reports (docs/PERFORMANCE.md).
    if (providers.gradleProperty("segue.composeReports").orNull == "true") {
        val reports = layout.buildDirectory.dir("compose-reports").get().asFile.absolutePath
        tasks.withType<KotlinJvmCompile>().matching { it.name == "compileDebugKotlin" }.configureEach {
            compilerOptions.freeCompilerArgs.addAll(
                "-P",
                "plugin:$COMPOSE_PLUGIN_ID:reportsDestination=$reports",
                "-P",
                "plugin:$COMPOSE_PLUGIN_ID:metricsDestination=$reports",
            )
        }
    }
}

private const val COMPOSE_PLUGIN_ID = "androidx.compose.compiler.plugins.kotlin"
