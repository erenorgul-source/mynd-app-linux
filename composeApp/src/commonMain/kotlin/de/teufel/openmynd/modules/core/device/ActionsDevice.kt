package de.teufel.openmynd.modules.core.device

import de.teufel.openmynd.modules.core.feature.*

/**
 * Interface representing devices using the Actions protocol (like MYND).
 * Defines features potentially common to Actions devices.
 */
sealed interface ActionsDevice : TeufelDeviceType {

    //Features common to all Actions devices
    override val features: Set<Feature>
        get() = setOf(
            Battery(),
            FirmwareVersion
        )
} 