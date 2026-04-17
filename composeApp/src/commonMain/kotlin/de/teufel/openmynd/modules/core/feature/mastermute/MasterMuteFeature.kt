package de.teufel.openmynd.modules.core.feature.mastermute

import de.teufel.openmynd.modules.core.feature.base.ToggleFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-specific interface for the master mute feature.
 */
interface MasterMuteFeature : ToggleFeature {
    /** Current mute status. */
    val isMuted: StateFlow<Boolean?>
    override val enabled: StateFlow<Boolean?> get() = isMuted
    
    /** Sets the mute status on the device. */
    override suspend fun set(enabled: Boolean): Boolean
} 