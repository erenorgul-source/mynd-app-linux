package de.teufel.openmynd.modules.core.feature.multipoint.actions

import de.teufel.openmynd.modules.core.feature.Multipoint
import de.teufel.openmynd.modules.core.feature.multipoint.MultipointFeature
import de.teufel.openmynd.modules.core.feature.base.BaseToggleFeature
import de.teufel.openmynd.modules.core.feature.base.ToggleCommandConfig
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import kotlinx.coroutines.flow.StateFlow

/**
 * Actions protocol implementation of MultipointFeature using BaseToggleFeature.
 */
class ActionsMultipointFeature(
    private val protocol: ActionsProtocol,
    definition: Multipoint = Multipoint
) : MultipointFeature {

    private val base = BaseToggleFeature(
        protocol = protocol,
        command = ToggleCommandConfig(
            getStatusCmd = ActionsCmd.GET_MULTIPOINT_STATUS.id,
            enableCmd = ActionsCmd.ENABLE_MULTIPOINT.id,
            disableCmd = ActionsCmd.DISABLE_MULTIPOINT.id,
            parseStatusFromAck = { payload -> if (payload.isNotEmpty()) payload[0].toInt() == 0x01 else null }
        ),
        valueRetrieval = definition.valueRetrieval
    )

    override val enabled: StateFlow<Boolean?> = base.enabled
    override suspend fun fetch(): Boolean = base.fetch()
    override suspend fun enable(): Boolean = base.enable()
    override suspend fun disable(): Boolean = base.disable()
}