package de.teufel.openmynd.modules.core.feature.mcufirmware

import de.teufel.openmynd.modules.core.feature.base.ReadFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-specific interface for retrieving the MCU firmware versions.
 * This feature supports multiple firmware targets (DSP and MCU).
 */
interface McuFirmwareVersionFeature : ReadFeature<String> {
    /** Available firmware targets to query versions for */
    val targets: Map<String, Byte>
    
    /** Current MCU firmware version. */
    val mcuVersion: StateFlow<String?>
    override val value: StateFlow<String?> get() = mcuVersion
    
    /** Current DSP firmware version. */
    val dspVersion: StateFlow<String?>
    
} 