package de.teufel.openmynd.modules.core.bluetooth.repository

import com.russhwolf.settings.Settings
import com.russhwolf.settings.NSUserDefaultsSettings
import platform.Foundation.NSUserDefaults

actual fun provideSettings(): Settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)


