import com.quantumff.segue.buildlogic.library
import com.quantumff.segue.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "com.google.devtools.ksp")
        dependencies {
            "ksp"(libs.library("hilt-compiler"))
        }

        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            dependencies {
                "implementation"(libs.library("hilt-core"))
            }
        }

        val configureAndroid = {
            apply(plugin = "com.google.dagger.hilt.android")
            dependencies {
                "implementation"(libs.library("hilt-android"))
            }
        }
        pluginManager.withPlugin("com.android.application") { configureAndroid() }
        // The Hilt plugin adds generated test sources, so modules with no tests would fail
        // Gradle's "no tests discovered" check.
        tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }
        pluginManager.withPlugin("com.android.library") { configureAndroid() }
    }
}
