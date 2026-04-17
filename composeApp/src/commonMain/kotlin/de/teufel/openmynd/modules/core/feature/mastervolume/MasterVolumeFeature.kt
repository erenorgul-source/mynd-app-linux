package de.teufel.openmynd.modules.core.feature.mastervolume

import de.teufel.openmynd.modules.core.feature.base.RangeFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-specific interface for the master volume feature.
 */
interface MasterVolumeFeature : RangeFeature {
    /** Current volume level (typically 0-100). */
    val volume: StateFlow<Int?>
    override val value: StateFlow<Int?> get() = volume
    
    /** Range of supported volume values. */
    override val range: IntRange
    
    /** Sets the volume level on the device. */
    override suspend fun set(value: Int): Boolean
}