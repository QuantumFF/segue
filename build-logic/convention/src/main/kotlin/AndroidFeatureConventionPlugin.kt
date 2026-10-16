import com.quantumff.segue.buildlogic.library
import com.quantumff.segue.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/**
 * A `feature:*` module: Compose UI, Hilt ViewModels, and type-safe navigation routes.
 * Features depend on core modules only, never on other features.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "segue.android.library.compose")
        apply(plugin = "segue.hilt")
        apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

        dependencies {
            "implementation"(project(":core:model"))
            "implementation"(project(":core:data"))
            "implementation"(project(":core:designsystem"))

            "implementation"(libs.library("androidx-compose-material3"))
            "implementation"(libs.library("androidx-lifecycle-runtime-compose"))
            "implementation"(libs.library("androidx-lifecycle-viewmodel-compose"))
            "implementation"(libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
            "implementation"(libs.library("androidx-navigation-compose"))
            "implementation"(libs.library("kotlinx-serialization-json"))
            "implementation"(libs.library("kotlinx-collections-immutable"))

            // ViewModel tests run against the fake repositories in core:testing.
            "testImplementation"(project(":core:testing"))
        }
    }
}
