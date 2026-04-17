package de.teufel.openmynd.modules.core.bluetooth.model

/**
 * Represents a Bluetooth Low Energy characteristic.
 */
data class BleCharacteristic(
    /**
     * UUID of the characteristic.
     */
    val uuid: String,
    
    /**
     * Properties describing the characteristic's capabilities.
     */
    val properties: Set<Property> = emptySet(),
    
    /**
     * UUID of the service this characteristic belongs to.
     */
    val serviceUuid: String
) {
    /**
     * Properties a characteristic can have.
     */
    @Suppress("unused")
    enum class Property {
        BROADCAST,
        READ,
        WRITE_NO_RESPONSE,
        WRITE,
        NOTIFY,
        INDICATE,
        SIGNED_WRITE,
        EXTENDED_PROPS
    }
} 