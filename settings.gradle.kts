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

// The owner's own additions (the folder `personal`), which the published code leaves out. They
// are built in whenever the folder is there; `-Pkiri.public=true` builds as if it were not.
if (file("personal/build.gradle.kts").exists() && providers.gradleProperty("kiri.public").orNull != "true") {
    include(":personal")
}
