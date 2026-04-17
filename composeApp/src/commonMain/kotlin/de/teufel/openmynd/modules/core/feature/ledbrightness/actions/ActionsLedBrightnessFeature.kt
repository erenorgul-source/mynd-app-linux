package de.teufel.openmynd.modules.core.feature.ledbrightness.actions

import de.teufel.openmynd.modules.core.feature.LedBrightness
import de.teufel.openmynd.modules.core.feature.ledbrightness.LedBrightnessFeature
import de.teufel.openmynd.modules.core.feature.base.BaseRangeFeature
import de.teufel.openmynd.modules.core.feature.base.RangeCommandConfig
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import kotlinx.coroutines.flow.StateFlow

/**
 * ACTIONS protocol implementation of the LedBrightnessFeature using BaseRangeFeature.
 */
class ActionsLedBrightnessFeature(
    private val protocol: ActionsProtocol,
    definition: LedBrightness = LedBrightness()
) : LedBrightnessFeature {

    private val base = BaseRangeFeature(
        protocol = protocol,
        command = RangeCommandConfig(
            getCmd = ActionsCmd.GET_LED_BRIGHTNESS.id,
            setCmd = ActionsCmd.SET_LED_BRIGHTNESS.id,
            parseFromAck = { payload -> if (payload.isNotEmpty()) payload[0].toInt() else null },
            range = 0..100
        ),
        valueRetrieval = definition.valueRetrieval
    )

    override val brightness: StateFlow<Int?> = base.value
    override val range: IntRange = base.range
    override suspend fun fetch(): Boolean = base.fetch()
    override suspend fun set(value: Int): Boolean = base.set(value)
}