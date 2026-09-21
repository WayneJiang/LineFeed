import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** `linefeed.hilt`: KSP + Hilt Android plugin and the matching runtime/compiler dependencies. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")

            dependencies {
                add("implementation", requireLibrary("hilt-android"))
                add("ksp", requireLibrary("hilt-compiler"))
            }
        }
    }
}
