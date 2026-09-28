package de.teufel.openmynd.modules.screens.permissions.di

import de.teufel.openmynd.modules.screens.permissions.PermissionManager
import org.koin.dsl.module
 
val permissionsModule = module {
    single { PermissionManager(get()) }
} 