package de.teufel.openmynd.modules.core.feature.batteryfriendly.actions

import de.teufel.openmynd.modules.core.feature.BatteryFriendlyCharging
import de.teufel.openmynd.modules.core.feature.batteryfriendly.BatteryFriendlyChargingFeature
import de.teufel.openmynd.modules.core.feature.base.BaseToggleFeature
import de.teufel.openmynd.modules.core.feature.base.ToggleCommandConfig
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import de.teufel.openmynd.modules.core.protocol.actions.ActionsNotification
import de.teufel.openmynd.modules.core.protocol.actions.withActionsNotificationDefault
import kotlinx.coroutines.flow.StateFlow

/**
 * Actions protocol implementation of BatteryFriendlyChargingFeature using BaseToggleFeature.
 */
class ActionsBatteryFriendlyChargingFeature(
    private val protocol: ActionsProtocol,
    definition: BatteryFriendlyCharging = BatteryFriendlyCharging
) : BatteryFriendlyChargingFeature {

    private val base = BaseToggleFeature(
        protocol = protocol,
        command = ToggleCommandConfig(
            getStatusCmd = ActionsCmd.GET_BATTERY_FRIENDLY_CHARGING_STATUS.id,
            enableCmd = ActionsCmd.ENABLE_BATTERY_FRIENDLY_CHARGING.id,
            disableCmd = ActionsCmd.DISABLE_BATTERY_FRIENDLY_CHARGING.id,
            parseStatusFromAck = { payload -> if (payload.isNotEmpty()) payload[0].toInt() == 0x01 else null },
            parseStatusFromNotification = { notif ->
                if (notif.payload.isNotEmpty() && notif.payload[0] == ActionsNotification.BATTERY_FRIENDLY_CHARGING_STATUS.id.toByte()) {
                    if (notif.payload.size >= 2) notif.payload[1].toInt() == 0x01 else null
                } else null
            }
        ),
        valueRetrieval = withActionsNotificationDefault(
            definition.valueRetrieval,
            ActionsNotification.BATTERY_FRIENDLY_CHARGING_STATUS
        )
    )

    override val isEnabled: StateFlow<Boolean?> = base.enabled
    override suspend fun fetch(): Boolean = base.fetch()
    override suspend fun enable(): Boolean = base.enable()
    override suspend fun disable(): Boolean = base.disable()
}