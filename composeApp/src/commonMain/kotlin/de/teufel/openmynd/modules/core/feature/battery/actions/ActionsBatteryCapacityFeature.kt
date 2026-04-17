package de.teufel.openmynd.modules.core.feature.battery.actions

import de.teufel.openmynd.modules.core.feature.BatteryCapacity
import de.teufel.openmynd.modules.core.feature.battery.BatteryCapacityFeature
import de.teufel.openmynd.modules.core.feature.base.BaseReadFeature
import de.teufel.openmynd.modules.core.feature.base.FetchCommandConfig
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import kotlinx.coroutines.flow.StateFlow

/**
 * Actions protocol implementation of BatteryCapacityFeature using BaseReadFeature for both values.
 */
class ActionsBatteryCapacityFeature(
    private val protocol: ActionsProtocol,
    definition: BatteryCapacity = BatteryCapacity
) : BatteryCapacityFeature {

    private val base = BaseReadFeature(
        protocol = protocol,
        command = FetchCommandConfig(
            cmdId = ActionsCmd.GET_BATTERY_CAPACITY.id,
            parseFromAck = { payload ->
                if (payload.size >= 4) {
                    val current = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                    val max = ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
                    current to max
                } else null
            }
        ),
        valueRetrieval = definition.valueRetrieval
    )

    override val currentCapacity: StateFlow<Int?> = base.value.mapPairFirst()
    override val maxCapacity: StateFlow<Int?> = base.value.mapPairSecond()
    override suspend fun fetch(): Boolean = base.fetch()
}

// Small helpers to project Pair<Int,Int> StateFlow to separate flows
private fun StateFlow<Pair<Int, Int>?>.mapPairFirst(): StateFlow<Int?> =
    de.teufel.openmynd.modules.core.utils.MapStateFlow.map(this) { it?.first }

private fun StateFlow<Pair<Int, Int>?>.mapPairSecond(): StateFlow<Int?> =
    de.teufel.openmynd.modules.core.utils.MapStateFlow.map(this) { it?.second }