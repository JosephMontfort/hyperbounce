plugins {
    id("com.android.application")
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
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    compileOnly("androidx.recyclerview:recyclerview:1.3.2")
    // Include Rhino for dynamic Runtime Scripting (Frida-like execution)
    implementation("org.mozilla:rhino:1.7.14")
}
