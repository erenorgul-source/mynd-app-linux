package de.teufel.openmynd.di

import dev.bluefalcon.ApplicationContext
import dev.bluefalcon.BlueFalcon
import dev.icerock.moko.permissions.PermissionsController
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModules(): Array<Module> = arrayOf(
    module {
        single { BlueFalcon(context = androidContext().applicationContext as ApplicationContext) }
        single { PermissionsController(androidContext()) }
    },
)