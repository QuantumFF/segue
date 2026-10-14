import com.android.build.api.dsl.ApplicationExtension
import com.quantumff.segue.buildlogic.configureKotlinAndroid
import com.quantumff.segue.buildlogic.configureUnitTests
import com.quantumff.segue.buildlogic.libs
import com.quantumff.segue.buildlogic.versionInt
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "com.android.application")

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig.targetSdk = libs.versionInt("targetSdk")
        }
        configureUnitTests()
    }
}
