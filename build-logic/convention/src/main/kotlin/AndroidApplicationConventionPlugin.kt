import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `linefeed.android.application`: base Android application module setup (compileSdk/minSdk/targetSdk/Java 17/desugaring). */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                defaultConfig {
                    targetSdk = 36
                }
                configureAndroidCommon(this)
            }
        }
    }
}
