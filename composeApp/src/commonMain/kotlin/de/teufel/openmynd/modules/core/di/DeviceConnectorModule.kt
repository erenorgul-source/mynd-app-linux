package de.teufel.openmynd.modules.core.di

import de.teufel.openmynd.modules.core.bluetooth.connector.BlueFalconDeviceConnector
import de.teufel.openmynd.modules.core.bluetooth.connector.DeviceConnector
import de.teufel.openmynd.modules.core.bluetooth.connector.KableDeviceConnector
import org.koin.dsl.module

enum class ConnectorBackend { BLUE_FALCON, KABLE }

internal var selectedBackend: ConnectorBackend = ConnectorBackend.BLUE_FALCON

val deviceConnectorModule = module {
    when (selectedBackend) {
        ConnectorBackend.BLUE_FALCON -> single<DeviceConnector> { BlueFalconDeviceConnector(get()) }
        ConnectorBackend.KABLE -> single<DeviceConnector> { KableDeviceConnector() }
    }
}