package de.teufel.openmynd.modules.core.di

import de.teufel.openmynd.modules.core.bluetooth.connector.BlueFalconDeviceConnector
import de.teufel.openmynd.modules.core.bluetooth.connector.DeviceConnector
import de.teufel.openmynd.modules.core.bluetooth.connector.KableDeviceConnector
import org.koin.dsl.module

val deviceConnectorModule = module {
    when (selectedBackend) {
        ConnectorBackend.BLUE_FALCON -> single<DeviceConnector> { BlueFalconDeviceConnector(get()) }
        ConnectorBackend.KABLE -> single<DeviceConnector> { KableDeviceConnector() }
    }
}
