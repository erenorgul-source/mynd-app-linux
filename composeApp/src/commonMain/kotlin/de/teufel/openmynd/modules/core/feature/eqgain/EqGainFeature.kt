package de.teufel.openmynd.modules.core.feature.eqgain

import de.teufel.openmynd.modules.core.feature.EqBand
import de.teufel.openmynd.modules.core.feature.base.ReadFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-specific interface for the EQ gain feature.
 */
interface EqGainFeature : ReadFeature<Map<EqBand, Int?>> {
    /**
     * Current gain values for each band.
     * The map key is the EqBand (e.g., EqBand.Bass, EqBand.Treble, etc.).
     * The value is the current gain value for that band.
     */
    val bandGains: StateFlow<Map<EqBand, Int?>>
    override val value: StateFlow<Map<EqBand, Int?>> get() = bandGains

    /**
     * The supported gain range for all bands.
     * For example: -6..6
     */
    val gainRange: IntRange

    /**
     * List of supported EQ bands.
     * For example: [EqBand.Bass, EqBand.Treble, EqBand.Mid]
     */
    val supportedBands: List<EqBand>

    /**
     * Fetches the current gain value for a specific band from the device.
     */
    suspend fun fetch(band: EqBand): Boolean

    /**
     * Sets the gain value for a specific band on the device.
     */
    suspend fun set(band: EqBand, gain: Int): Boolean
}