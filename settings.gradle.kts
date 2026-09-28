rootProject.name = "OpenMynd"
include(":composeApp")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

/*
 * The Android target is optional so the Linux desktop client can be built without an
 * Android SDK (e.g. from an AUR PKGBUILD). It is enabled when an SDK is detected, and can
 * be forced either way with `-Popenmynd.android=true|false`.
 */
val androidSdkDetected = providers.environmentVariable("ANDROID_HOME").isPresent ||
    providers.environmentVariable("ANDROID_SDK_ROOT").isPresent ||
    file("local.properties").let { it.isFile && it.readText().contains("sdk.dir") }
gradle.extra["openmynd.android"] =
    providers.gradleProperty("openmynd.android").orNull?.toBoolean() ?: androidSdkDetected
