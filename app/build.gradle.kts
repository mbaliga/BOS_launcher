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

android {
    namespace = "com.bos.sphere"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bos.sphere"
        minSdk = 31
        targetSdk = 35
        versionCode = gitCount
        versionName = "0.2.0"
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "GIT_DATE", "\"$gitDate\"")
    }

    buildTypes {
        release {
            // Optimised (R8) build for daily use; signed with the debug key so it installs without a keystore.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    implementation(project(":core-catalog"))

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
}
