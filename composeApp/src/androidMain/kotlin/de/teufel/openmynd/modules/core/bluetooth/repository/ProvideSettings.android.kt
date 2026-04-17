package de.teufel.openmynd.modules.core.bluetooth.repository

import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import de.teufel.openmynd.AndroidApp

actual fun provideSettings(): Settings = SharedPreferencesSettings(AndroidApp.INSTANCE.getSharedPreferences("openmynd_prefs", 0))


