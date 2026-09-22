pluginManagement {
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

rootProject.name = "MidnightDancerKmp"

include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":core:navigation")
include(":shared")
include(":androidApp")
