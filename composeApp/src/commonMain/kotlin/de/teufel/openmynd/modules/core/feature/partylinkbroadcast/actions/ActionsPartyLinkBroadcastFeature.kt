package de.teufel.openmynd.modules.core.feature.partylinkbroadcast.actions

import de.teufel.openmynd.modules.core.feature.PartyLinkBroadcast
import de.teufel.openmynd.modules.core.feature.partylinkbroadcast.PartyLinkBroadcastFeature
import de.teufel.openmynd.modules.core.feature.base.BaseToggleFeature
import de.teufel.openmynd.modules.core.feature.base.ToggleCommandConfig
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import de.teufel.openmynd.modules.core.protocol.actions.ActionsNotification
import de.teufel.openmynd.modules.core.protocol.actions.withActionsNotificationDefault
import kotlinx.coroutines.flow.StateFlow

/**
 * Actions protocol implementation of PartyLinkBroadcastFeature using BaseToggleFeature.
 */
class ActionsPartyLinkBroadcastFeature(
    private val protocol: ActionsProtocol,
    definition: PartyLinkBroadcast = PartyLinkBroadcast
) : PartyLinkBroadcastFeature {

    private val base = BaseToggleFeature(
        protocol = protocol,
        command = ToggleCommandConfig(
            getStatusCmd = ActionsCmd.GET_PARTYLINK_BROADCAST_STATUS.id,
            enableCmd = ActionsCmd.START_PARTYLINK_BROADCAST.id,
            disableCmd = ActionsCmd.STOP_PARTYLINK_BROADCAST.id,
            parseStatusFromAck = { payload -> payload.firstOrNull()?.toInt()?.let { it == 1 } },
            parseStatusFromNotification = { notif ->
                if (notif.payload.firstOrNull() == ActionsNotification.PARTY_LINK_STATUS.id.toByte())
                    notif.payload.getOrNull(1)?.toInt()?.let { it == 1 } else null
            }
        ),
        valueRetrieval = withActionsNotificationDefault(
            definition.valueRetrieval,
            ActionsNotification.PARTY_LINK_STATUS
        )
    )

    override val isActive: StateFlow<Boolean?> = base.enabled
    override suspend fun fetch(): Boolean = base.fetch()
    override suspend fun start(): Boolean = base.enable()
    override suspend fun stop(): Boolean = base.disable()
}