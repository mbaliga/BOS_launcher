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

rootProject.name = "SphereLauncher"

include(":app")
include(":core-design")
include(":core-data")
include(":feature-sphere")
include(":feature-search")
include(":search-engine")
include(":feature-grid")
include(":shaders")
include(":core-capabilities")
include(":core-launch")
include(":core-input")
include(":feature-desktop")
