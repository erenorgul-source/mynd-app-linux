package de.teufel.openmynd.di

import de.teufel.openmynd.modules.core.di.appModule
import de.teufel.openmynd.modules.core.di.ConnectorBackend
import de.teufel.openmynd.modules.core.di.selectedBackend
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module

expect fun platformModules(): Array<Module>

fun initKoin(connectorBackend: ConnectorBackend? = null, additionalKoinConfig: KoinApplication.() -> Unit = {}) {
    connectorBackend?.let { selectedBackend = it }
    startKoin {
        modules(
            appModule,
            *platformModules()
        )
        additionalKoinConfig()
    }
}