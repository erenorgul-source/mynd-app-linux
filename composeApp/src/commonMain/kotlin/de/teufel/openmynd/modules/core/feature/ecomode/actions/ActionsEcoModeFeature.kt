package de.teufel.openmynd.modules.core.feature.ecomode.actions

import de.teufel.openmynd.modules.core.feature.EcoMode
import de.teufel.openmynd.modules.core.feature.ecomode.EcoModeFeature
import de.teufel.openmynd.modules.core.feature.base.BaseToggleFeature
import de.teufel.openmynd.modules.core.feature.base.ToggleCommandConfig
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import de.teufel.openmynd.modules.core.protocol.actions.ActionsNotification
import de.teufel.openmynd.modules.core.protocol.actions.withActionsNotificationDefault
import kotlinx.coroutines.flow.StateFlow

/**
 * Actions protocol implementation of EcoModeFeature using BaseToggleFeature.
 */
class ActionsEcoModeFeature(
    private val protocol: ActionsProtocol,
    definition: EcoMode = EcoMode
) : EcoModeFeature {

    private val base = BaseToggleFeature(
        protocol = protocol,
        command = ToggleCommandConfig(
            getStatusCmd = ActionsCmd.GET_ECO_MODE_STATUS.id,
            enableCmd = ActionsCmd.ENABLE_ECO_MODE.id,
            disableCmd = ActionsCmd.DISABLE_ECO_MODE.id,
            parseStatusFromAck = { payload -> if (payload.isNotEmpty()) payload[0].toInt() == 0x01 else null },
            parseStatusFromNotification = { notif ->
                if (notif.payload.isNotEmpty() && notif.payload[0] == ActionsNotification.ECO_MODE_STATUS.id.toByte()) {
                    if (notif.payload.size >= 2) (notif.payload[1].toInt() == 0x01) else null
                } else null
            }
        ),
        valueRetrieval = withActionsNotificationDefault(
            definition.valueRetrieval,
            ActionsNotification.ECO_MODE_STATUS
        )
    )

    override val isEnabled: StateFlow<Boolean?> = base.enabled
    override suspend fun fetch(): Boolean = base.fetch()
    override suspend fun enable(): Boolean = base.enable()
    override suspend fun disable(): Boolean = base.disable()
}