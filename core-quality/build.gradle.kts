plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val robolectricRuntime: Configuration by configurations.creating { isTransitive = false }
val robolectricJarDir = layout.buildDirectory.dir("robolectric-jars")
val robolectricJars = tasks.register<Copy>("robolectricJars") {
    from(robolectricRuntime)
    into(robolectricJarDir)
}

android {
    namespace = "com.bos.sphere.core.quality"
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
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    robolectricRuntime("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
}
