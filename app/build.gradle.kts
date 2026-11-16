plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Build identity shown in Settings → About, taken from git so every APK is traceable to a commit.
fun git(vararg args: String): String = providers.exec {
    commandLine("git", *args)
    workingDir = rootDir
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim()
val gitSha = git("rev-parse", "--short", "HEAD").ifEmpty { "unknown" }
val gitDate = git("show", "-s", "--format=%cs", "HEAD").ifEmpty { "unknown" }
val gitCount = git("rev-list", "--count", "HEAD").toIntOrNull() ?: 1

// CFG-17: whether the Lab category is listed in release builds. Default true while the project is alpha; the owner flips
// the default before a public build, or a build passes -Pliftoff.lab=false.
val labVisibleRelease: Boolean = (findProperty("liftoff.lab") as String?)?.trim()?.lowercase() != "false"

// INP-01, INP-05: the app module's own JVM + Robolectric test setup (same offline runtime jars as feature-grid).
val robolectricRuntime14: Configuration by configurations.creating { isTransitive = false }
val robolectricRuntime15: Configuration by configurations.creating { isTransitive = false }
val robolectricJarDir = layout.buildDirectory.dir("robolectric-jars")
val robolectricJars = tasks.register<Copy>("robolectricJars") {
    from(robolectricRuntime14)
    from(robolectricRuntime15)
    into(robolectricJarDir)
}

android {
    namespace = "com.bos.sphere"
    compileSdk = 35

    defaultConfig {
        // CFG-20: the application ID is an open owner decision (docs/decisions/CFG-20.md). The default is unchanged; a trial
        // build can use another one with -Pliftoff.applicationId=<id> without editing this file. Code never reads the literal
        // (it uses packageName), so the Kotlin namespace stays com.bos.sphere either way.
        applicationId = (findProperty("liftoff.applicationId") as String?)?.takeIf { it.isNotBlank() } ?: "com.bos.sphere"
        minSdk = 31
        targetSdk = 35
        versionCode = gitCount
        versionName = "0.2.0"
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "GIT_DATE", "\"$gitDate\"")
    }

    buildTypes {
        debug {
            // CFG-17: the Lab (spikes) category is always listed in debug builds.
            buildConfigField("boolean", "LAB_VISIBLE", "true")
        }
        release {
            // CFG-17: alpha default is visible so spikes can run on the release APK; -Pliftoff.lab=false hides it (docs/decisions/CFG-17.md).
            buildConfigField("boolean", "LAB_VISIBLE", labVisibleRelease.toString())
            // Optimised (R8) build for daily use; signed with the debug key so it installs without a keystore.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // NFR-01 to NFR-05: the variant the macrobenchmarks (benchmark/, not in the offline build) install: release code,
        // debug key, profileable (src/benchmark/AndroidManifest.xml). Never shipped.
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            isDebuggable = false
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.dependsOn(robolectricJars)
            test.systemProperty("robolectric.offline", "true")
            test.systemProperty("robolectric.dependency.dir", robolectricJarDir.get().asFile.absolutePath)
        }
    }
}

dependencies {
    implementation(project(":core-design"))
    implementation(project(":core-data"))
    implementation(project(":feature-search"))
    implementation(project(":feature-grid"))
    implementation(project(":shaders"))
    implementation(project(":core-capabilities"))
    implementation(project(":core-launch"))
    implementation(project(":core-input"))
    implementation(project(":core-quality"))
    implementation(project(":core-catalog"))
    implementation(project(":core-signals"))
    implementation(project(":feature-dock"))
    implementation(project(":feature-desktop"))
    implementation(project(":feature-launchpad"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    robolectricRuntime14("org.robolectric:android-all-instrumented:14-robolectric-10818077-i7")
    robolectricRuntime15("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// NFR-08: fail when the release APK is over the download budget (see apk-size.gradle.kts).
apply(from = "apk-size.gradle.kts")
