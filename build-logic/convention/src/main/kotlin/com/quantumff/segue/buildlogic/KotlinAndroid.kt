package com.quantumff.segue.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

private val JAVA_VERSION = JavaVersion.VERSION_17
private val JVM_TARGET = JvmTarget.JVM_17
private const val TEST_JDK = 21

/** Shared Android + Kotlin setup for application, library, and test modules. AGP 9 compiles Kotlin itself. */
internal fun Project.configureKotlinAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.versionInt("compileSdk")
        defaultConfig.minSdk = libs.versionInt("minSdk")
        defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        compileOptions.sourceCompatibility = JAVA_VERSION
        compileOptions.targetCompatibility = JAVA_VERSION
        // Robolectric needs merged resources and the merged manifest.
        testOptions.unitTests.isIncludeAndroidResources = true
        // Shared robolectric.properties (pins the sandbox SDK; see the file for why).
        sourceSets.getByName("test").resources.directories.add(
            rootProject.file("gradle/robolectric").absolutePath,
        )
    }
    configureKotlinCompiler()
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
    configureKotlinCompiler()
}

private fun Project.configureKotlinCompiler() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JVM_TARGET)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }
}

/**
 * Unit-test stack every module gets. Robolectric and MockWebServer are added per module.
 *
 * Tests run on a JDK 21 toolchain whatever JDK runs Gradle: Robolectric needs 21 for SDK 36.
 */
internal fun Project.configureUnitTests() {
    val launcher = extensions.getByType<JavaToolchainService>().launcherFor {
        languageVersion.set(JavaLanguageVersion.of(TEST_JDK))
    }
    tasks.withType<Test>().configureEach {
        javaLauncher.set(launcher)
        // Robolectric's SDK 36 sandbox reaches into JDK internals.
        jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
    }

    dependencies {
        "testImplementation"(libs.library("junit4"))
        "testImplementation"(libs.library("truth"))
        "testImplementation"(libs.library("turbine"))
        "testImplementation"(libs.library("kotlinx-coroutines-test"))
    }
}
