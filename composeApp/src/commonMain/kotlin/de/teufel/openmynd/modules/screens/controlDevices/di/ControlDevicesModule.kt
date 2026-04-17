package de.teufel.openmynd.modules.screens.controlDevices.di

import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val controlDevicesModule = module {
    factoryOf(::ControlDevicesViewModel)
}