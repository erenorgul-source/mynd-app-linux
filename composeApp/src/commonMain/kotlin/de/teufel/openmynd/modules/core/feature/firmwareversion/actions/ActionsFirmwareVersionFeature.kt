package de.teufel.openmynd.modules.core.feature.firmwareversion.actions

import de.teufel.openmynd.modules.core.feature.FirmwareVersion
import de.teufel.openmynd.modules.core.feature.firmwareversion.FirmwareVersionFeature
import de.teufel.openmynd.modules.core.feature.base.BaseReadFeature
import de.teufel.openmynd.modules.core.feature.base.FetchCommandConfig
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import kotlinx.coroutines.flow.StateFlow

/**
 * Actions protocol implementation of FirmwareVersionFeature using BaseReadFeature.
 */
class ActionsFirmwareVersionFeature(
    private val protocol: ActionsProtocol,
    definition: FirmwareVersion = FirmwareVersion
) : FirmwareVersionFeature {

    private val base = BaseReadFeature(
        protocol = protocol,
        command = FetchCommandConfig(
            cmdId = ActionsCmd.GET_BT_FIRMWARE_VERSION.id,
            parseFromAck = { payload -> if (payload.isNotEmpty()) payload.decodeToString() else null }
        ),
        valueRetrieval = definition.valueRetrieval
    )

    override val version: StateFlow<String?> = base.value
    override suspend fun fetch(): Boolean = base.fetch()
}