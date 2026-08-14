plugins {
    id("com.android.application") version "9.1.1" apply false
    // Needed to host the real com.kyant.backdrop / LiquidToggle Compose UI inside
    // the hooked system apps (see app/build.gradle.kts + the liquidtoggle package).
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
