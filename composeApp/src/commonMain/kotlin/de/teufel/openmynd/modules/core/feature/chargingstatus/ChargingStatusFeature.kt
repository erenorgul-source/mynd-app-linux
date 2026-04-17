package de.teufel.openmynd.modules.core.feature.chargingstatus

import de.teufel.openmynd.modules.core.feature.base.ReadFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-specific interface for the charging status feature.
 */
interface ChargingStatusFeature : ReadFeature<Boolean> {
    /** Current charging status: true if connected. */
    val isConnected: StateFlow<Boolean?>
    override val value: StateFlow<Boolean?> get() = isConnected

} 