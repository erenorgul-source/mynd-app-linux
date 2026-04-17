package de.teufel.openmynd.modules.core.feature.multipoint

import de.teufel.openmynd.modules.core.feature.base.ToggleFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents the device's multipoint capability, which allows
 * connection to multiple Bluetooth devices simultaneously.
 */
interface MultipointFeature : ToggleFeature {
    /** Current status of multipoint connectivity (enabled/disabled). */
    override val enabled: StateFlow<Boolean?>
    
    /**
     * Enable multipoint connectivity.
     * @return true if the command was successfully sent and acknowledged.
     */
    override suspend fun enable(): Boolean
    
    /**
     * Disable multipoint connectivity.
     * @return true if the command was successfully sent and acknowledged.
     */
    override suspend fun disable(): Boolean

    override suspend fun set(enabled: Boolean): Boolean =
        if (enabled) enable() else disable()
} 