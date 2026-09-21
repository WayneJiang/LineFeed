import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `linefeed.android.compose`: enables Compose on whichever Android extension (application or
 * library) is already applied to this module, and wires the Compose BOM + core artifacts.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.getByType(CommonExtension::class.java).apply {
                buildFeatures.compose = true
            }

            dependencies {
                val bom = requireLibrary("androidx-compose-bom")
                add("implementation", platform(bom))
                add("implementation", requireLibrary("androidx-compose-ui"))
                add("implementation", requireLibrary("androidx-compose-ui-tooling-preview"))
                add("implementation", requireLibrary("androidx-compose-material3"))
                add("debugImplementation", requireLibrary("androidx-compose-ui-tooling"))
            }
        }
    }
}
