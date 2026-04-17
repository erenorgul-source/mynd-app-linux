package de.teufel.openmynd.modules.core.device

import de.teufel.openmynd.modules.core.feature.Feature
import kotlin.reflect.KClass

/**
 * Base interface for all Teufel device types.
 * Defines common properties and capabilities.
 */
sealed interface TeufelDeviceType {
    val features: Set<Feature>
    val bluetoothNames: Set<String>
    val publicName: String

    /**
     * Checks if any of the device's known Bluetooth names match the provided name (case-insensitive).
     */
    fun anyBluetoothName(bluetoothName: String?): Boolean {
        return bluetoothName != null && bluetoothNames.any { bluetoothName.contains(it, ignoreCase = true) }
    }
}

val ALL_SUPPORTED_DEVICES: List<TeufelDeviceType> = listOf(
    Mynd
)

/**
 * Helper function to find a device type by name using the central list.
 */
fun findDeviceTypeByName(name: String?): TeufelDeviceType? {
    if (name == null) return null
    return ALL_SUPPORTED_DEVICES.find { it.anyBluetoothName(name) }
}

enum class TeufelDeviceColor(val colorId: Int) {
    WARM_BLACK(19),
    WARM_WHITE(20),
    LIGHT_MINT(21),
    WILD_BERRY(22);

    companion object {
        fun of(value: Int): TeufelDeviceColor = entries.firstOrNull { it.colorId == value } ?: WARM_BLACK }
}