import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Shared compileSdk/minSdk/targetSdk/Java-17/desugaring setup used by both the
 * `application` and `library` Android convention plugins.
 *
 * AGP 9's `CommonExtension` is no longer generic (unlike AGP 8), so a single
 * lambda-accepting function is enough for both plugins instead of the
 * `CommonExtension<*, *, *, *, *, *>` workaround older AGP versions needed.
 */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk = 37

        defaultConfig.apply {
            minSdk = 24
        }

        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            isCoreLibraryDesugaringEnabled = true
        }
    }

    dependencies {
        add("coreLibraryDesugaring", requireLibrary("desugar-jdk-libs"))
    }

    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

internal fun Project.requireLibrary(alias: String) =
    versionCatalog.findLibrary(alias).orElseThrow { IllegalStateException("Missing catalog entry $alias") }
