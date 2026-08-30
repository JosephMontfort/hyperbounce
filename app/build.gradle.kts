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
        // Merge note: floating-nav branch declared minSdk 33, liquid-toggles branch
        // declared minSdk 31. We keep the higher (more conservative) bound so neither
        // feature set ends up installable on an OS level its own branch never declared
        // support for. See MERGE_NOTES.md.
        minSdk = 33
        targetSdk = 37
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
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "androiddebugkey"
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
    compileOnly("androidx.recyclerview:recyclerview:1.3.2")
    // Merge note: both branches compiled against the same io.github.libxposed:api:102.0.0
    // artifact, but fetched it differently (floating-nav via the api.xposed.info Maven repo,
    // liquid-toggles via a vendored local AAR). We standardize on the local AAR: it's the
    // exact same artifact, already vendored in-repo, and removes a third-party Maven host
    // as a build-time dependency. This is a build-time-only change (compileOnly is not
    // packaged into the module) and does not affect compiled bytecode or runtime behavior.
    compileOnly(files("libs/api-102.0.0.aar"))
    // Phase 2: lets the companion app itself (not the hooks) write settings the hooks can
    // read back via XposedInterface.getRemotePreferences(group) — see
    // prefs/HyperBouncePrefs.kt and ui/state/XposedServiceBridge.kt for the full mechanism.
    // Unlike the hook API above, this runs in the app's own process, so it's a normal
    // `implementation`, not `compileOnly`.
    implementation("io.github.libxposed:service:102.0.0")

    // Match the liquid-glass library's published Compose Multiplatform Android line.
    // (Identical versions across both source branches.)
    implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
    implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
    implementation("org.jetbrains.compose.ui:ui:1.11.1")
    implementation("org.jetbrains.compose.ui:ui-graphics:1.11.1")
    implementation("org.jetbrains.compose.material:material-ripple:1.11.1")
    // Added for the settings UI (Phase 1): screen-to-screen push/pop transitions
    // (AnimatedContent). The rest of the app's animation (springs, Animatable) already
    // came transitively via compose.foundation, same as every other file in this repo,
    // but AnimatedContent lives in the separate "animation" artifact, so it's declared
    // explicitly here rather than assumed transitive.
    implementation("org.jetbrains.compose.animation:animation:1.11.1")
    // Proper vector icon set — replacing the hand-drawn Canvas icons used through Phase 1,
    // which read as low-quality at small sizes (tab bar, menu label). material-icons-core
    // only (not -extended): it's a self-contained icon asset library, not the full Material
    // component system, and every icon this app needs (Settings, Tune, ArrowBack, Refresh,
    // ContentCopy, Info, Check, MoreHoriz, PowerSettingsNew) is in the core set.
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")

    // Kyant's actual Liquid Glass backdrop engine. Shared by the nav-bar engine
    // (dev.joseph.hyperbounce.nav.glass) and the toggle/slider engine
    // (dev.joseph.hyperbounce.toggles.glass).
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("io.github.kyant0:shapes:1.2.0")

    // Existing Compose-in-Xposed infrastructure.
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.9.0")
    implementation("androidx.savedstate:savedstate:1.3.0")
}
