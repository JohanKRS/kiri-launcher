pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "kiri-launcher"

include(":app")
include(":core")

// The owner's own app (the folder `personal`, a private repository cloned there), which the
// published code leaves out. It is a separate app that shares `core`, never part of Kiri; it is
// built here whenever the folder is there, and `-Pkiri.public=true` builds as if it were not.
if (file("personal/build.gradle.kts").exists() && providers.gradleProperty("kiri.public").orNull != "true") {
    include(":personal")
}
