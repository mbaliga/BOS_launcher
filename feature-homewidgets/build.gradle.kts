plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Sphere's own home-screen App Widgets (providers any launcher can place). Not to be confused with
// :feature-widgets, which *hosts* other apps' widgets inside Sphere. See docs/widgets.md.
android {
    namespace = "com.bos.sphere.feature.homewidgets"
    compileSdk = 35

    defaultConfig {
        minSdk = 31
    }

    buildFeatures {
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    // JVM unit tests for the pure widget logic. Test classpath only; nothing ships in the APK.
    testImplementation("junit:junit:4.13.2")
}
