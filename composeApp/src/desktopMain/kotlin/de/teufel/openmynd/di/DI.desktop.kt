package de.teufel.openmynd.di

import de.teufel.openmynd.modules.core.bluetooth.bluez.BlueZClient
import de.teufel.openmynd.modules.core.bluetooth.bluez.BlueZDeviceConnector
import de.teufel.openmynd.modules.core.bluetooth.connector.DeviceConnector
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModules(): Array<Module> = arrayOf(
    module {
        // Connect to BlueZ right away so the Bluetooth state is known by the first frame.
        single(createdAtStart = true) { BlueZClient().apply { start() } }
        single<DeviceConnector> { BlueZDeviceConnector(get()) }
    },
)
