package de.teufel.openmynd.modules.screens.knownDevices.di

import de.teufel.openmynd.modules.core.bluetooth.repository.KnownDevicesRepository
import de.teufel.openmynd.modules.screens.knownDevices.KnownDevicesViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val knownDevicesModule = module {
    singleOf(::KnownDevicesRepository)
    factoryOf(::KnownDevicesViewModel)
}