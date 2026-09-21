import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `linefeed.android.feature`: everything a `feature:*` module needs — library + Compose + Hilt +
 * serialization, plus the standard dependencies on `core:domain`/`core:designsystem` and (test-only)
 * `core:testing`. Feature modules never see `core:data` directly (enforced simply by never adding it here).
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("linefeed.android.library")
            pluginManager.apply("linefeed.android.compose")
            pluginManager.apply("linefeed.hilt")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                add("implementation", project(":core:domain"))
                add("implementation", project(":core:designsystem"))
                add("implementation", requireLibrary("androidx-lifecycle-runtime-compose"))
                add("implementation", requireLibrary("androidx-lifecycle-viewmodel-compose"))
                add("implementation", requireLibrary("androidx-navigation-compose"))
                add("implementation", requireLibrary("androidx-hilt-lifecycle-viewmodel-compose"))
                add("implementation", requireLibrary("kotlinx-serialization-json"))
                add("testImplementation", project(":core:testing"))
            }
        }
    }
}
