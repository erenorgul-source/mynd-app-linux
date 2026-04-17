package de.teufel.openmynd.modules.core.feature.battery.actions

import de.teufel.openmynd.modules.core.feature.Battery
import de.teufel.openmynd.modules.core.feature.battery.BatteryFeature
import de.teufel.openmynd.modules.core.feature.base.BaseReadFeature
import de.teufel.openmynd.modules.core.feature.base.FetchCommandConfig
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import de.teufel.openmynd.modules.core.protocol.actions.ActionsNotification
import de.teufel.openmynd.modules.core.protocol.actions.withActionsNotificationDefault
import kotlinx.coroutines.flow.StateFlow

/**
 * ACTIONS protocol implementation of BatteryFeature using BaseReadFeature with notifications.
 */
class ActionsBatteryFeature(
    private val protocol: ActionsProtocol,
    definition: Battery = Battery()
) : BatteryFeature {

    private val base = BaseReadFeature(
        protocol = protocol,
        command = FetchCommandConfig(
            cmdId = ActionsCmd.GET_BATTERY_LEVEL.id,
            parseFromAck = { payload ->
                // ACK payload for Actions typically starts immediately after the success byte.
                // Battery level appears as the first data byte; fall back logic keeps compatibility.
                when {
                    payload.isEmpty() -> null
                    payload.size >= 2 && (payload[1].toInt() and 0xFF) != 0 -> (payload[1].toInt() and 0xFF)
                    else -> (payload[0].toInt() and 0xFF)
                }
            },
            parseFromNotification = { notif ->
                // Filter for EVENT_NOTIFICATION with BATTERY_LEVEL payload
                if (notif.cmdId == ActionsCmd.EVENT_NOTIFICATION.id &&
                    notif.payload.isNotEmpty() &&
                    notif.payload[0] == ActionsNotification.BATTERY_LEVEL.id.toByte()) {
                    if (notif.payload.size >= 2) notif.payload[1].toInt() and 0xFF else null
                } else null
            }
        ),
        valueRetrieval = withActionsNotificationDefault(
            definition.valueRetrieval,
            ActionsNotification.BATTERY_LEVEL
        )
    )

    override val level: StateFlow<Int?> = base.value
    override suspend fun fetch(): Boolean = base.fetch()
}