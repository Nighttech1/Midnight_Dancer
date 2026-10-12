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
include(":core:tts")
include(":feature:splash:logic")
include(":feature:elements:logic")
include(":feature:music:logic")
include(":feature:choreography:logic")
include(":feature:trainer:logic")
include(":feature:exchange:logic")
include(":feature:settings:logic")
include(":shared")
include(":androidApp")
