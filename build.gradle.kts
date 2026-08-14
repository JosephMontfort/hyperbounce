plugins {
    id("com.android.application") version "8.4.2" apply false
    // Needed to host the real com.kyant.backdrop / LiquidToggle Compose UI inside
    // the hooked system apps (see app/build.gradle.kts + the liquidtoggle package).
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
