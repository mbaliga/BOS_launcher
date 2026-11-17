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
include(":core-quality")
include(":core-catalog")
include(":core-signals")
include(":feature-dock")
include(":feature-desktop")
include(":feature-launchpad")
include(":crash-recovery")

// NFR-01 to NFR-05: the macrobenchmark module needs androidx.benchmark from the network, so it is opt-in:
// ./gradlew -Pliftoff.benchmark=true :benchmark:connectedBenchmarkAndroidTest (see tools/device_nfr.sh, benchmark/README.md).
if (providers.gradleProperty("liftoff.benchmark").isPresent && providers.gradleProperty("liftoff.benchmark").get() != "false") include(":benchmark")
