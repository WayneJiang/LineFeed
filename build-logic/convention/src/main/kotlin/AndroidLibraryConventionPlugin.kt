import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `linefeed.android.library`: base Android library module setup, plus the JUnit4/coroutines-test/turbine
 * test dependencies every module's unit tests rely on.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                testOptions {
                    unitTests.isIncludeAndroidResources = true
                }
                configureAndroidCommon(this)
            }

            dependencies {
                add("testImplementation", requireLibrary("junit"))
                add("testImplementation", requireLibrary("kotlinx-coroutines-test"))
                add("testImplementation", requireLibrary("turbine"))
            }

            // `unitTests.isIncludeAndroidResources = true` (needed later for Robolectric) makes AGP
            // generate a `R.class` for the debugUnitTest variant even when a module has *no* test
            // sources at all. Gradle 9's default `failOnNoDiscoveredTests` then sees that stray
            // compiled class and fails with "test sources present ... but did not discover any
            // tests", even though there is no real test source directory. Real test failures are
            // unaffected: once a module has actual `@Test`s they are always discovered normally, so
            // this only suppresses the false positive for empty/no-test modules. See docs/NOTES.md.
            tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach {
                failOnNoDiscoveredTests.set(false)
            }
        }
    }
}
