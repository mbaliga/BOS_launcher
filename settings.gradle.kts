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

// Narrow, deliberate addition: brings in mbaliga/Hyle-Design-System ONLY for its
// :crash-recovery module (dev.aarso:crash-recovery — no dependency on :hyle). core-design's
// own "Hyle-consumer debt" (re-implementing the visual language rather than depending on the
// actual dev.aarso:hyle library) is untouched — this doesn't resolve or reference :hyle at
// all. Update the pin with:
//   git -C hyle-design-system fetch && git -C hyle-design-system checkout <sha> && git add hyle-design-system
includeBuild("hyle-design-system")

rootProject.name = "SphereLauncher"

include(":app")
include(":core-design")
include(":core-data")
include(":feature-sphere")
include(":feature-search")
include(":shaders")
