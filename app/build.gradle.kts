plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

android {
    namespace = "dev.joseph.hyperbounce"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.joseph.hyperbounce"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("RELEASE_STORE_FILE") ?: "debug.keystore")
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "androiddebugkey"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(files("libs/api-102.0.0.aar"))

    // Compose versions match the AndroidLiquidGlass 2.0.0 toolchain used by
    // the already-working Liquid Glass floating-navigation experiment.
    implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
    implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
    implementation("org.jetbrains.compose.ui:ui:1.11.1")
    implementation("org.jetbrains.compose.ui:ui-graphics:1.11.1")
    implementation("org.jetbrains.compose.material:material-ripple:1.11.1")

    // Actual Kyant Liquid Glass backdrop/refraction engine.
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("io.github.kyant0:shapes:1.2.0")

    // ComposeView injection into Xposed/system-app hierarchies.
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.9.0")
    implementation("androidx.savedstate:savedstate:1.3.0")
}
