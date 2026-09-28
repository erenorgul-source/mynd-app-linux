package de.teufel.openmynd.utils

enum class Platform {
    ANDROID, IOS, LINUX
}

expect fun getCurrentPlatform(): Platform
