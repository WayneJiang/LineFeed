pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LineFeed"

include(":app")
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":core:testing")
include(":feature:feed")
include(":feature:detail")
include(":feature:saved")
