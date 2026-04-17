package de.teufel.openmynd.modules.core.protocol

import de.teufel.openmynd.modules.core.bluetooth.connector.DeviceConnector
import de.teufel.openmynd.modules.core.device.ActionsDevice
import de.teufel.openmynd.modules.core.device.TeufelDeviceType
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol

/**
 * Creates protocol instances for a given device type.
 */
interface ProtocolFactory {
    fun create(deviceType: TeufelDeviceType, connector: DeviceConnector): DeviceProtocol?
}

/**
 * Default protocol resolution strategy.
 */
class DefaultProtocolFactory : ProtocolFactory {
    override fun create(deviceType: TeufelDeviceType, connector: DeviceConnector): DeviceProtocol? =
        when (deviceType) {
            is ActionsDevice -> ActionsProtocol(connector)
        }
}
