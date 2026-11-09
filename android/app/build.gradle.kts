plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "dev.hyperios.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.hyperios.app"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }
    }

    buildFeatures {
        compose = true
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
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly(files("libs/api-102.0.0.aar"))
    compileOnly("androidx.recyclerview:recyclerview:1.3.2")
    compileOnly("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("io.github.libxposed:service:102.0.0")

    // Retained for injected Xposed hooks rendering in target apps (Instagram/WhatsApp/SystemUI)
    implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
    implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
    implementation("org.jetbrains.compose.ui:ui:1.11.1")
    implementation("org.jetbrains.compose.ui:ui-graphics:1.11.1")
    implementation("org.jetbrains.compose.material:material-ripple:1.11.1")
    implementation("org.jetbrains.compose.animation:animation:1.11.1")
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")

    // Backdrop engine for injected hooks
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("io.github.kyant0:shapes:1.2.0")

    // Compose infrastructure for hooks
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.9.0")
    implementation("androidx.savedstate:savedstate:1.3.0")
}
