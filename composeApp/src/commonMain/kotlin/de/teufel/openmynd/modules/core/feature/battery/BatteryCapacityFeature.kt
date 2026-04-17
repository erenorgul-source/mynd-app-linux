package de.teufel.openmynd.modules.core.feature.battery

import de.teufel.openmynd.modules.core.feature.base.ReadFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-specific interface for the Battery Capacity feature.
 */
interface BatteryCapacityFeature : ReadFeature<Int> {
    /** Current capacity in mAh */
    val currentCapacity: StateFlow<Int?>
    override val value: StateFlow<Int?> get() = currentCapacity
    
    /** Maximum capacity in mAh */
    val maxCapacity: StateFlow<Int?>
    
} 