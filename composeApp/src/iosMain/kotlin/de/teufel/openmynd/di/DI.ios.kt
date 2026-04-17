package de.teufel.openmynd.di

import dev.bluefalcon.BlueFalcon
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.ios.PermissionsController as IOSPermissionsController
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.UIKit.UIApplication

actual fun platformModules(): Array<Module> = arrayOf(
    module {
        single { BlueFalcon(context = UIApplication.sharedApplication) }
        factory<PermissionsController> { IOSPermissionsController() }
    }
)