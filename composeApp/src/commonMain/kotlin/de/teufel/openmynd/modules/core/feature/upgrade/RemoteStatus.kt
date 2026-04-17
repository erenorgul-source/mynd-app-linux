package de.teufel.openmynd.modules.core.feature.upgrade

data class RemoteStatus(
    val versionName: String?,
    val boardName: String?,
    val hardwareRev: String?,
    val batteryThreshold: Int,
    val versionCode: Int,
    val featureSupport: Int
)
