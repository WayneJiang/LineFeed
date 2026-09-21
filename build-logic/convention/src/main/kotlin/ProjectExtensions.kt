import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Shorthand to reach the root `libs` version catalog from convention plugins.
 *
 * Deliberately NOT named `libs`: convention plugin classes end up on the compile classpath of
 * every build script that applies them, and a top-level `Project.libs` extension property here
 * would shadow Gradle's own generated type-safe `libs` accessor in those scripts (breaking
 * `libs.androidx.core.ktx`-style access with "unresolved reference" errors).
 */
val Project.versionCatalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
