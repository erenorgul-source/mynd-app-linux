package de.teufel.openmynd.modules.core.feature.devicecolor

import de.teufel.openmynd.modules.core.feature.base.ReadFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-specific interface for the device color feature.
 */
interface DeviceColorFeature : ReadFeature<Int> {
    /** Current device color ID. */
    val colorId: StateFlow<Int?>
    override val value: StateFlow<Int?> get() = colorId

} 