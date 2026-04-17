package de.teufel.openmynd.modules.core.feature.batteryfriendly

import de.teufel.openmynd.modules.core.feature.base.ToggleFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-specific interface for the Battery Friendly Charging feature.
 */
interface BatteryFriendlyChargingFeature : ToggleFeature {
    /** Current state of Battery Friendly Charging (on/off). */
    val isEnabled: StateFlow<Boolean?>
    override val enabled: StateFlow<Boolean?> get() = isEnabled
    
    override suspend fun set(enabled: Boolean): Boolean =
        if (enabled) enable() else disable()
} 