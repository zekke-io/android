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

rootProject.name = "android"

includeBuild("../mobile-base") {
    dependencySubstitution {
        substitute(module("zekke:mobile-base")).using(project(":"))
    }
}

include(":app")
include(":ui")
include(":platform")
include(":feature:onboarding")
include(":feature:unlock")
include(":feature:passwords")
include(":feature:tabs")
include(":feature:vault")
include(":feature:notes")
include(":feature:drive")
include(":feature:sharing")
include(":feature:settings")
