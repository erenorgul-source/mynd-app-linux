package de.teufel.openmynd.modules.screens.searchDevices.di

import de.teufel.openmynd.modules.screens.searchDevices.SearchDevicesViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val searchDevicesModule = module {
    factoryOf(::SearchDevicesViewModel)
}