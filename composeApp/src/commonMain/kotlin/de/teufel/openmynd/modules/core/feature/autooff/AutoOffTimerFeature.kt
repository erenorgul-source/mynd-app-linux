package de.teufel.openmynd.modules.core.feature.autooff

import de.teufel.openmynd.modules.core.feature.base.SelectorFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-specific interface for the auto-off timer feature.
 * Controls the time in seconds until the device automatically powers off.
 */
interface AutoOffTimerFeature : SelectorFeature<Int> {
    /**
     * Current auto-off timer value in seconds.
     * 0 means the timer is disabled.
     */
    val seconds: StateFlow<Int?>
    override val selected: StateFlow<Int?> get() = seconds
    
    /**
     * The set of timer values supported by this device, in seconds.
     * Typically includes 0 (off), 600 (10 minutes), 1800 (30 minutes), 3600 (60 minutes).
     */
    val supportedSeconds: Set<Int>

    /**
     * Sets the auto-off timer value on the device.
     * @param seconds The timer value in seconds, must be one of the supportedSeconds values
     * @return true if the command was sent successfully, false otherwise
     */
    suspend fun set(seconds: Int): Boolean

    override suspend fun select(value: Int): Boolean = set(value)
} 