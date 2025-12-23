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

rootProject.name = "AnimalTracking"
include(":app")
include(":core:core-android")
include(":core:core-tracking")
include(":domain")
include(":data")
include(":ai")
include(":feature:camera")
include(":feature:horse-tracking")
