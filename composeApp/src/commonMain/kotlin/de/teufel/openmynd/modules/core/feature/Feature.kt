package de.teufel.openmynd.modules.core.feature

import de.teufel.openmynd.modules.core.feature.base.ValueRetrieval
import de.teufel.openmynd.modules.core.feature.base.ValueRetrieval.FetchingOnly

/**
 * Base marker interface for all device features.
 */
interface Feature {
    /** How this feature's value is retrieved/updated for a given device. */
    val valueRetrieval: ValueRetrieval get() = FetchingOnly
}

// --- Features based on existing functionality ---

/**
 * Represents the battery level feature.
 */
data class Battery(val isTws: Boolean = false, override val valueRetrieval: ValueRetrieval = FetchingOnly) : Feature

/**
 * Represents the auto-off timer feature.
 * @param supportedSeconds The set of allowed timer durations in seconds (e.g., {0, 600, 1800, 3600}).
 */
data class AutoOffTimer(val supportedSeconds: Set<Int> = setOf(0, 300, 600, 900, 1800, 3600, 10800), override val valueRetrieval: ValueRetrieval = FetchingOnly) : Feature

/**
 * Represents the ability to retrieve the firmware version.
 */
data object FirmwareVersion : Feature {
    override val valueRetrieval: ValueRetrieval = FetchingOnly
}

/**
 * Represents the status of the power adapter connection.
 */
data object ChargingStatus : Feature {
    override val valueRetrieval: ValueRetrieval = FetchingOnly
}

/**
 * Represents the ability to retrieve the device color ID.
 * @param skipSuccess If true, the protocol will skip the success byte for this command.
 */
data class DeviceColor(val skipSuccess: Boolean = false, override val valueRetrieval: ValueRetrieval = FetchingOnly) : Feature

/**
 * Represents the sound icons feature (enable/disable).
 */
data class SoundIcons(
    val inverted: Boolean = true,
    override val valueRetrieval: ValueRetrieval = FetchingOnly
) : Feature

/**
 * Represents the multipoint connection feature (enable/disable).
 */
data object Multipoint : Feature {
    override val valueRetrieval: ValueRetrieval = FetchingOnly
}

// --- Additional features for future expansion ---
// object AirohaUpgrade : Feature
// object PlayfulEqualizer : Feature

/**
 * Represents the Actions OTA firmware upgrade feature.
 * @param needsChargerConnected Whether the device must be plugged in to perform the upgrade.
 */
data class ActionsUpgrade(val needsChargerConnected: Boolean = false) : Feature

// --- Features Added for MYND (Actions Protocol) --- 

/**
 * Represents the Master Volume control feature.
 * @param range The supported volume range (e.g., 0 to 100).
 */
data class MasterVolume(
    val range: IntRange = 0..100,
    override val valueRetrieval: ValueRetrieval = FetchingOnly
) : Feature

/**
 * Represents the Master Mute control feature.
 */
data object MasterMute : Feature {
    override val valueRetrieval: ValueRetrieval = FetchingOnly
}

/**
 * Interface for EQ band types to make the code more type-safe.
 */
interface EqBand {
    object Bass : EqBand
    object Mid : EqBand
    object Treble : EqBand
    
    /**
     * Convert band to protocol index
     */
    fun toIndex(): Int = when (this) {
        is Bass -> 0
        is Mid -> 1
        is Treble -> 2
        else -> throw IllegalArgumentException("Unsupported band type: $this")
    }
    
    companion object {
        /**
         * Convert protocol index to band
         */
        fun fromIndex(index: Int): EqBand = when (index) {
            0 -> Bass
            1 -> Mid
            2 -> Treble
            else -> throw IllegalArgumentException("Unsupported band index: $index")
        }
    }
}

/**
 * Represents the EQ Parameter Gain feature (e.g., Bass, Treble).
 * @param bands List of supported EQ bands (e.g., Bass, Treble, Mid)
 * @param gainRange The supported gain range (e.g., -6..6)
 * @param gainMultiplier The factor the gain value is multiplied by in the protocol (e.g., 10).
 */
data class EqGain(
    val bands: List<EqBand> = listOf(EqBand.Bass, EqBand.Treble),
    val gainRange: IntRange = -6..6,
    val gainMultiplier: Int = 10,
    override val valueRetrieval: ValueRetrieval = FetchingOnly
) : Feature

/**
 * Represents the ECO Mode feature.
 */
data object EcoMode : Feature {
    override val valueRetrieval: ValueRetrieval = FetchingOnly
}

/**
 * Represents the ability to retrieve the MCU firmware version.
 * @param builtInDsp Whether DSP firmware version is available on this device.
 */
data class McuFirmwareVersion(
    val builtInDsp: Boolean = true,
    override val valueRetrieval: ValueRetrieval = FetchingOnly
) : Feature

/**
 * Represents the PartyLink Broadcast feature.
 */
data object PartyLinkBroadcast : Feature {
    override val valueRetrieval: ValueRetrieval = FetchingOnly
}

/**
 * Represents the Source Selection feature.
 * @param sources Map of source names (e.g., "Bluetooth") to protocol byte codes (e.g., 0x00).
 */
data class SourceSelection(
    val sources: Map<String, Byte> = mapOf("Bluetooth" to 0x00, "AUX" to 0x01, "USB" to 0x02),
    override val valueRetrieval: ValueRetrieval = FetchingOnly
) : Feature

/**
 * Represents the Battery Capacity feature (current and max).
 */
data object BatteryCapacity : Feature {
    override val valueRetrieval: ValueRetrieval = FetchingOnly
}

/**
 * Represents the Battery Friendly Charging feature.
 */
data object BatteryFriendlyCharging : Feature {
    override val valueRetrieval: ValueRetrieval = FetchingOnly
}

/**
 * Represents the LED Brightness control feature.
 * @param range The supported brightness range (e.g., 0 to 100).
 */
data class LedBrightness(
    val range: IntRange = 0..100,
    override val valueRetrieval: ValueRetrieval = FetchingOnly
) : Feature 