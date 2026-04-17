package de.teufel.openmynd.modules.core.feature.autooff.actions

import de.teufel.openmynd.modules.core.feature.AutoOffTimer
import de.teufel.openmynd.modules.core.feature.autooff.AutoOffTimerFeature
import de.teufel.openmynd.modules.core.feature.base.BaseRangeFeature
import de.teufel.openmynd.modules.core.feature.base.RangeCommandConfig
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import kotlinx.coroutines.flow.StateFlow

/**
 * Actions protocol implementation of AutoOffTimerFeature using BaseRangeFeature.
 */
class ActionsAutoOffTimerFeature(
    private val protocol: ActionsProtocol,
    definition: AutoOffTimer
) : AutoOffTimerFeature {

    override val supportedSeconds: Set<Int> = definition.supportedSeconds

    private val base = BaseRangeFeature(
        protocol = protocol,
        command = RangeCommandConfig(
            getCmd = ActionsCmd.GET_AUTO_OFF_TIMER.id,
            setCmd = ActionsCmd.SET_AUTO_OFF_TIMER.id,
            parseFromAck = { payload ->
                if (payload.size >= 2) {
                    (((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF))
                } else null
            },
            range = 0..65535,
            buildSetPayload = { value -> byteArrayOf(((value ushr 8) and 0xFF).toByte(), (value and 0xFF).toByte()) }
        ),
        valueRetrieval = definition.valueRetrieval
    )

    override val seconds: StateFlow<Int?> = base.value

    override suspend fun fetch(): Boolean = base.fetch()

    override suspend fun set(seconds: Int): Boolean {
        if (seconds !in supportedSeconds) return false
        return base.set(seconds)
    }
}