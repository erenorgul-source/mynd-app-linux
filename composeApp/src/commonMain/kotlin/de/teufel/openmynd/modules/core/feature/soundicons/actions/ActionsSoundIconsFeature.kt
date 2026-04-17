package de.teufel.openmynd.modules.core.feature.soundicons.actions

import de.teufel.openmynd.modules.core.feature.SoundIcons
import de.teufel.openmynd.modules.core.feature.soundicons.SoundIconsFeature
import de.teufel.openmynd.modules.core.feature.base.BaseToggleFeature
import de.teufel.openmynd.modules.core.feature.base.ToggleCommandConfig
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import de.teufel.openmynd.modules.core.protocol.actions.ActionsNotification
import de.teufel.openmynd.modules.core.protocol.actions.withActionsNotificationDefault
import kotlinx.coroutines.flow.StateFlow

/**
 * Actions protocol implementation of SoundIconsFeature using BaseToggleFeature.
 */
class ActionsSoundIconsFeature(
    private val protocol: ActionsProtocol,
    definition: SoundIcons
) : SoundIconsFeature {

    private val base = BaseToggleFeature(
        protocol = protocol,
        command = ToggleCommandConfig(
            getStatusCmd = ActionsCmd.GET_SOUND_ICONS_STATUS.id,
            enableCmd = ActionsCmd.ENABLE_SOUND_ICONS.id,
            disableCmd = ActionsCmd.DISABLE_SOUND_ICONS.id,
            parseStatusFromAck = { payload ->
                if (payload.isNotEmpty()) {
                    val bit = if (definition.inverted) 0x00 else 0x01
                    (payload[0].toInt() == bit)
                } else null
            },
            parseStatusFromNotification = { notif ->
                if (notif.payload.isNotEmpty() && notif.payload[0] == ActionsNotification.SOUND_ICONS_STATUS.id.toByte()) {
                    if (notif.payload.size >= 2) {
                        val bit = if (definition.inverted) 0x00 else 0x01
                        (notif.payload[1].toInt() == bit)
                    } else null
                } else null
            }
        ),
        valueRetrieval = withActionsNotificationDefault(
            definition.valueRetrieval,
            ActionsNotification.SOUND_ICONS_STATUS
        )
    )

    override val enabled: StateFlow<Boolean?> = base.enabled
    override suspend fun fetch(): Boolean = base.fetch()
    override suspend fun enable(): Boolean = base.enable()
    override suspend fun disable(): Boolean = base.disable()
}