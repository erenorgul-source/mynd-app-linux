package de.teufel.openmynd.modules.core.feature.battery

import de.teufel.openmynd.modules.core.feature.base.ReadFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-specific interface for the battery feature.
 */
interface BatteryFeature : ReadFeature<Int> {
    /** Current battery level as percentage (0-100). */
    val level: StateFlow<Int?>
    override val value: StateFlow<Int?> get() = level

}