package de.teufel.openmynd.modules.core.feature.ecomode

import de.teufel.openmynd.modules.core.feature.base.ToggleFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-specific interface for the ECO mode feature.
 */
interface EcoModeFeature : ToggleFeature {
    /** Current state of ECO mode (on/off). */
    val isEnabled: StateFlow<Boolean?>
    override val enabled: StateFlow<Boolean?> get() = isEnabled
    
    override suspend fun set(enabled: Boolean): Boolean =
        if (enabled) enable() else disable()
} 