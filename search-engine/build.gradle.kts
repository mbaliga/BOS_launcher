// :search-engine — a vendored copy of Shared-Libraries-asoc's :search-core (dev.aarso:search-core 0.2.0):
// the app-agnostic query language, facet evaluation, natural-language interpretation and ranking.
// Plain Kotlin/JVM on purpose (no Android, no coroutines), exactly as upstream.
//
// Vendored rather than consumed with includeBuild because the upstream build pins AGP 8.9.1 and this
// launcher is on 8.7.3 (see that repo's "AGP lockstep" note). To update: re-copy
// search-core/src/main/kotlin/dev/aarso/search/*.kt from Shared-Libraries-asoc (source of truth) and
// bump the version note below.
//   vendored from: mbaliga/Shared-Libraries-asoc @ d4ade55 (search-core 0.2.0)
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(false)
    }
}
