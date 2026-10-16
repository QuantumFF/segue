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

        listOf("com.android.application", "com.android.library").forEach { androidPlugin ->
            pluginManager.withPlugin(androidPlugin) {
                apply(plugin = "com.google.dagger.hilt.android")
                dependencies {
                    "implementation"(libs.library("hilt-android"))
                }
                // The Hilt plugin adds generated test sources, so a module without tests would
                // otherwise fail Gradle's "no tests discovered" check.
                tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }
            }
        }
    }
}
