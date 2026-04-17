package de.teufel.openmynd.modules.core.feature.devicecolor.actions

import de.teufel.openmynd.modules.core.feature.DeviceColor
import de.teufel.openmynd.modules.core.feature.devicecolor.DeviceColorFeature
import de.teufel.openmynd.modules.core.feature.base.BaseReadFeature
import de.teufel.openmynd.modules.core.feature.base.FetchCommandConfig
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import kotlinx.coroutines.flow.StateFlow

/**
 * Actions protocol implementation of DeviceColorFeature using BaseReadFeature.
 */
class ActionsDeviceColorFeature(
    private val protocol: ActionsProtocol,
    definition: DeviceColor = DeviceColor()
) : DeviceColorFeature {

    private val base = BaseReadFeature(
        protocol = protocol,
        command = FetchCommandConfig(
            cmdId = ActionsCmd.GET_DEVICE_COLOR.id,
            parseFromAck = { payload -> payload.firstOrNull()?.toInt() }
        ),
        valueRetrieval = definition.valueRetrieval
    )

    override val colorId: StateFlow<Int?> = base.value
    override suspend fun fetch(): Boolean = base.fetch()
}