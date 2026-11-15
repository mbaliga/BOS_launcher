// Opt-in: included by settings.gradle.kts only with -Pliftoff.benchmark=true (needs network for androidx.benchmark). See README.md.
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bos.sphere.benchmark"
    compileSdk = 35
    defaultConfig {
        minSdk = 31
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
}

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.test.ext:junit:1.2.1")
}
