package de.teufel.openmynd.modules.core.bluetooth.repository

import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import de.teufel.openmynd.utils.XdgDirs
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Settings stored in `$XDG_CONFIG_HOME/openmynd/settings.properties`, written atomically on
 * every change.
 */
actual fun provideSettings(): Settings = propertiesFileSettings(File(XdgDirs.appConfig, "settings.properties"))

internal fun propertiesFileSettings(file: File): Settings {
    val properties = Properties()
    if (file.isFile) {
        runCatching { file.inputStream().use(properties::load) }
    }
    return PropertiesSettings(properties) { updated ->
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().use { updated.store(it, "OpenMynd settings") }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
