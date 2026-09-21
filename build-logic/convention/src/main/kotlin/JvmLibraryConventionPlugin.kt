import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `linefeed.jvm.library`: plain Kotlin/JVM module (used by `core:domain` and `core:testing`) so
 * their logic can be unit tested without an Android runtime/emulator.
 *
 * Deliberately does NOT use `kotlin { jvmToolchain(17) }`: this machine only has JDK 21 installed,
 * and a toolchain request for 17 makes Gradle try to auto-provision/download one. Setting
 * source/targetCompatibility (and the Kotlin compiler's jvmTarget) directly targets bytecode 17
 * while still compiling with the JDK 21 that is actually running Gradle.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }

            dependencies {
                add("implementation", requireLibrary("kotlinx-coroutines-core"))
                add("testImplementation", requireLibrary("junit"))
                add("testImplementation", requireLibrary("kotlinx-coroutines-test"))
                add("testImplementation", requireLibrary("turbine"))
            }
        }
    }
}
