package de.teufel.openmynd.modules.ui.theme

import de.teufel.openmynd.utils.XdgDirs
import de.teufel.openmynd.utils.portal.XdgDesktopPortal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Works out whether the desktop uses a dark color scheme. Compose's `isSystemInDarkTheme()`
 * does not know about Linux desktops, so this checks, in order:
 *
 * 1. `OPENMYND_THEME=dark|light` (manual override),
 * 2. the XDG portal `color-scheme` setting (KDE Plasma, GNOME, and others; live updates),
 * 3. the KDE color scheme in `kdeglobals` (window background brightness),
 * 4. a GTK theme name containing "dark" (`GTK_THEME`).
 */
class LinuxThemeDetector(
    private val portal: XdgDesktopPortal,
    private val kdeGlobals: File = File(XdgDirs.configHome, "kdeglobals"),
    private val env: (String) -> String? = System::getenv,
) {
    /** true = dark, false = light, null = unknown. Blocking (may do a D-Bus call). */
    fun isDark(): Boolean? {
        when (env("OPENMYND_THEME")?.lowercase()) {
            "dark" -> return true
            "light" -> return false
        }
        when (portal.readColorScheme()) {
            PORTAL_DARK -> return true
            PORTAL_LIGHT -> return false
        }
        isKdeColorSchemeDark(kdeGlobals)?.let { return it }
        env("GTK_THEME")?.let { if (it.contains("dark", ignoreCase = true)) return true }
        return null
    }

    /** Re-evaluates [isDark] whenever the portal reports a color-scheme change. */
    fun watch(onChanged: (Boolean?) -> Unit): AutoCloseable? =
        portal.watchColorScheme { onChanged(isDark()) }

    companion object {
        private const val PORTAL_DARK = 1
        private const val PORTAL_LIGHT = 2

        /**
         * Reads `[Colors:Window] BackgroundNormal=r,g,b` from a kdeglobals file and treats a
         * dark window background as a dark scheme (that is also what the KDE portal does).
         */
        fun isKdeColorSchemeDark(kdeGlobals: File): Boolean? {
            if (!kdeGlobals.isFile) return null
            var inWindowColors = false
            for (raw in kdeGlobals.readLines()) {
                val line = raw.trim()
                if (line.startsWith("[")) {
                    inWindowColors = line == "[Colors:Window]"
                } else if (inWindowColors && line.startsWith("BackgroundNormal=")) {
                    val rgb = line.substringAfter('=').split(',').mapNotNull { it.trim().toIntOrNull() }
                    if (rgb.size >= 3) {
                        val luminance = (0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]) / 255
                        return luminance < 0.5
                    }
                }
            }
            return null
        }
    }
}

/**
 * App-wide dark mode state for the desktop client. [start] resolves the initial value before
 * the first window is shown (so there's no light/dark flash) and then follows the desktop.
 */
object LinuxTheme {
    private val _isDark = MutableStateFlow<Boolean?>(null)
    val isDark: StateFlow<Boolean?> = _isDark.asStateFlow()

    private var watch: AutoCloseable? = null

    fun start(detector: LinuxThemeDetector = LinuxThemeDetector(XdgDesktopPortal())) {
        if (watch != null) return
        _isDark.value = detector.isDark()
        watch = detector.watch { _isDark.value = it }
    }
}
