package de.teufel.openmynd.utils

import java.io.File

/** XDG base directories (https://specifications.freedesktop.org/basedir-spec/latest/). */
internal object XdgDirs {
    private const val APP_DIR = "openmynd"

    private val home: File get() = File(System.getProperty("user.home"))

    private fun dir(envVar: String, fallback: String): File =
        System.getenv(envVar)?.takeIf { it.startsWith("/") }?.let(::File) ?: File(home, fallback)

    /** `$XDG_CONFIG_HOME` (default `~/.config`). */
    val configHome: File get() = dir("XDG_CONFIG_HOME", ".config")

    /** App specific config directory, e.g. `~/.config/openmynd`. */
    val appConfig: File get() = File(configHome, APP_DIR)
}
