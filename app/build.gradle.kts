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

    buildFeatures {
        compose = true
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
    kotlinOptions {
        jvmTarget = "17"
        // THE COMPILER MUZZLE: Stops the K2 compiler from crashing on older AndroidX metadata
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
}

// Bypass AAR Metadata checks blocking modern dependencies
tasks.configureEach {
    if (name.contains("AarMetadata")) {
        enabled = false
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("io.github.kyant0:shapes:1.2.0")
}
