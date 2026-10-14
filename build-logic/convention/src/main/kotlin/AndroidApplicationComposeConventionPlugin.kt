import com.android.build.api.dsl.ApplicationExtension
import com.quantumff.segue.buildlogic.configureAndroidCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType

class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "segue.android.application")
        configureAndroidCompose(extensions.getByType<ApplicationExtension>())
    }
}
