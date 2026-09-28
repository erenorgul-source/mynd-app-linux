buildscript {
    // The Android Gradle Plugin is only put on the classpath when the Android target is
    // enabled (see settings.gradle.kts), so desktop-only builds never need Google's Maven.
    if (gradle.extra["openmynd.android"] as Boolean) {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath(libs.android.gradlePlugin)
        }
    }
}

plugins {
    alias(libs.plugins.multiplatform).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.compose).apply(false)
    alias(libs.plugins.kotlinx.serialization).apply(false)
}
