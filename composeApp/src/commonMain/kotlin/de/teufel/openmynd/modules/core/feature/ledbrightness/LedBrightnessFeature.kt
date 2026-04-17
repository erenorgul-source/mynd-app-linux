package de.teufel.openmynd.modules.core.feature.ledbrightness

import de.teufel.openmynd.modules.core.feature.base.RangeFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-specific interface for the LED brightness feature.
 */
interface LedBrightnessFeature : RangeFeature {
    /** Current LED brightness level. */
    val brightness: StateFlow<Int?>
    override val value: StateFlow<Int?> get() = brightness
    
    /** Range of supported brightness values. */
    override val range: IntRange
    
    /** Sets the LED brightness level on the device. */
    override suspend fun set(value: Int): Boolean
} 