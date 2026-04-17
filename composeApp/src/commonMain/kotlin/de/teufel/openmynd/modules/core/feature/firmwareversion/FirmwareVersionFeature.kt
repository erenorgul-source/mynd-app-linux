package de.teufel.openmynd.modules.core.feature.firmwareversion

import de.teufel.openmynd.modules.core.feature.base.ReadFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-specific interface for retrieving the firmware version.
 */
interface FirmwareVersionFeature : ReadFeature<String> {
    /** Current firmware version string. */
    val version: StateFlow<String?>
    override val value: StateFlow<String?> get() = version

} 