import androidx.room.gradle.RoomExtension
import com.quantumff.segue.buildlogic.library
import com.quantumff.segue.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "androidx.room")
        apply(plugin = "com.google.devtools.ksp")

        extensions.configure<RoomExtension> {
            // Exported schemas are committed and used by migration tests.
            schemaDirectory("$projectDir/schemas")
        }

        dependencies {
            "implementation"(libs.library("androidx-room-runtime"))
            "implementation"(libs.library("androidx-room-paging"))
            "ksp"(libs.library("androidx-room-compiler"))
            "testImplementation"(libs.library("androidx-room-testing"))
        }
    }
}
