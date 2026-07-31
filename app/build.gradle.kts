plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "2.0.0"
    // The Magic Bullet: Native K2 Compose Compiler
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
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
    
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    
    // NOTE: composeOptions block deleted! K2 compiler handles this natively now.
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly("de.robv.android.xposed:api:82:sources")

    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.animation:animation")

    // Miuix Navigation Bar components
    implementation("top.yukonga.miuix.kmp:miuix:0.2.0")

    // Lifecycle spoofers for Xposed Injection
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.savedstate:savedstate:1.2.1")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
