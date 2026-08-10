plugins {
    id("com.android.application") version "9.1.1" apply false
    // Required because Compose needs this even though AGP 9's built-in Kotlin
    // means we don't apply org.jetbrains.kotlin.android anywhere anymore.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
}
