// :crash-recovery — a vendored copy of Shared-Libraries-asoc's :crash-recovery (dev.aarso:crash-recovery 1.4.0):
// the owner's shared crash-capture and recovery-screen utility (splash, readable report, Copy, Save file, Share,
// Continue, loop-gated Reset). Plain android.widget views and ZERO dependencies on purpose, exactly as upstream: the
// screen must stay survivable when Compose, the theme or the stores are what crashed. No network, nothing is uploaded.
//
// Vendored rather than consumed with includeBuild because the upstream build pins AGP 8.9.1 and this launcher is on
// 8.7.3 (see that repo's "AGP lockstep" note), the same reason :search-engine is vendored. To update: re-copy
// crash-recovery/src from Shared-Libraries-asoc (source of truth, never edit the copy here) and bump the note below.
// Differences from upstream build file: compileSdk 35 and minSdk 31 (this repo's), no maven-publish.
//   vendored from: mbaliga/Shared-Libraries-asoc @ d4ade55 (crash-recovery 1.4.0)
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.aarso.crashrecovery"
    compileSdk = 35

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation(libs.junit)
}
