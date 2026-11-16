plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.convertapity.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.convertapity.app"
        minSdk = 29
        // targetSdk 28 on purpose: Android 10+ blocks running downloaded binaries for apps targeting 29+
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }

    // Committed keystore so every CI build is signed with the same key (updates install over each other).
    // Fine for sideloading a hobby app; use a private keystore if you ever publish it elsewhere.
    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "convertapity"
            keyAlias = "convertapity"
            keyPassword = "convertapity"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("roborazzi.test.record", "true") }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    // Pinned: the last release line that ships the Material 3 Expressive components and still builds on AGP 8
    implementation("androidx.compose.material3:material3") { version { strictly("1.4.0-alpha18") } }
    implementation("androidx.compose.material3:material3-android") { version { strictly("1.4.0-alpha18") } }
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    // Screenshot rendering of the UI on the JVM (./gradlew testDebugUnitTest -> app/build/screenshots)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.50.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.50.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
