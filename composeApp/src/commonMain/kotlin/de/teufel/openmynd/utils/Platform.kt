package de.teufel.openmynd.utils

enum class Platform {
    ANDROID, IOS
}

expect fun getCurrentPlatform(): Platform 