package de.teufel.openmynd.modules.core.feature.mastermute.actions

import de.teufel.openmynd.modules.core.feature.MasterMute
import de.teufel.openmynd.modules.core.feature.mastermute.MasterMuteFeature
import de.teufel.openmynd.modules.core.feature.base.BaseToggleFeature
import de.teufel.openmynd.modules.core.feature.base.ToggleCommandConfig
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import de.teufel.openmynd.modules.core.protocol.actions.ActionsNotification
import de.teufel.openmynd.modules.core.protocol.actions.withActionsNotificationDefault
import kotlinx.coroutines.flow.StateFlow

/**
 * Actions protocol implementation of MasterMuteFeature using BaseToggleFeature.
 */
class ActionsMasterMuteFeature(
    private val protocol: ActionsProtocol,
    definition: MasterMute = MasterMute
) : MasterMuteFeature {

    private val base = BaseToggleFeature(
        protocol = protocol,
        command = ToggleCommandConfig(
            getStatusCmd = ActionsCmd.GET_MASTER_MUTE_STATUS.id,
            enableCmd = ActionsCmd.SET_MASTER_MUTE_STATUS.id, // treat enable as mute=1
            disableCmd = ActionsCmd.SET_MASTER_MUTE_STATUS.id, // treat disable as mute=0
            parseStatusFromAck = { payload -> if (payload.isNotEmpty()) payload[0].toInt() == 0x01 else null },
            parseStatusFromNotification = { notif ->
                if (notif.payload.isNotEmpty() && notif.payload[0] == ActionsNotification.MASTER_MUTE_STATUS.id.toByte()) {
                    if (notif.payload.size >= 2) notif.payload[1].toInt() == 0x01 else null
                } else null
            }
        ),
        valueRetrieval = withActionsNotificationDefault(
            definition.valueRetrieval,
            ActionsNotification.MASTER_MUTE_STATUS
        )
    )

    override val isMuted: StateFlow<Boolean?> = base.enabled
    override suspend fun fetch(): Boolean = base.fetch()
    override suspend fun set(enabled: Boolean): Boolean = if (enabled) base.enable() else base.disable()
}