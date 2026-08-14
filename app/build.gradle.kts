plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.joseph.hyperbounce"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.joseph.hyperbounce"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("RELEASE_STORE_FILE") ?: "debug.keystore")
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly("de.robv.android.xposed:api:82:sources")

    // --- Real AndroidLiquidGlass (Kyant0/AndroidLiquidGlass) toggle -----------------
    // The toggle is now hosted as the library's actual LiquidToggle composable
    // (adapted, not reimplemented) instead of a hand-painted Canvas port. These are
    // the same Compose Multiplatform coordinates backdrop:2.0.0 / shapes:1.2.0 were
    // themselves compiled against (see the upstream repo's gradle/libs.versions.toml),
    // used here so the classes we link against match exactly.
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("io.github.kyant0:shapes:1.2.0")
    implementation("org.jetbrains.compose.ui:ui:1.11.0")
    implementation("org.jetbrains.compose.ui:ui-graphics:1.11.0")
    implementation("org.jetbrains.compose.foundation:foundation:1.11.0")

    // ComposeView needs a Lifecycle/ViewModelStore/SavedStateRegistry owner reachable
    // from the view tree. Settings/Dialer Activities already provide one; SystemUI
    // windows added straight onto WindowManager (e.g. Control Center) usually don't,
    // so LiquidToggleHost installs a minimal fallback owner using these.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.savedstate:savedstate-ktx:1.3.2")
}
