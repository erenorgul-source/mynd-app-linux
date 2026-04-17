package de.teufel.openmynd.modules.core.di

import de.teufel.openmynd.modules.core.feature.DeviceFeatureProvider
import de.teufel.openmynd.modules.core.protocol.DefaultProtocolFactory
import de.teufel.openmynd.modules.core.protocol.ProtocolFactory
import de.teufel.openmynd.modules.screens.permissions.di.permissionsModule
import de.teufel.openmynd.modules.screens.controlDevices.di.controlDevicesModule
import de.teufel.openmynd.modules.screens.knownDevices.di.knownDevicesModule
import de.teufel.openmynd.modules.screens.searchDevices.di.searchDevicesModule
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Main Koin module aggregating other modules.
 */
val appModule = module {
    includes(
        controlDevicesModule,
        deviceConnectorModule,
        knownDevicesModule,
        permissionsModule,
        searchDevicesModule,
    )

    single<ProtocolFactory> { DefaultProtocolFactory() }
    singleOf(::DeviceFeatureProvider)
}