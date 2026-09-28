package de.teufel.openmynd.utils

import co.touchlab.kermit.Logger
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.util.concurrent.TimeUnit

/**
 * Process-level tweaks that make the JVM behave like a native Linux desktop app.
 * [configureBeforeAwt] must run before any window is created.
 */
object LinuxDesktopIntegration {
    /** Must match `StartupWMClass` in the .desktop file. */
    const val WM_CLASS = "openmynd"

    private val logger = Logger.withTag("Desktop")

    fun configureBeforeAwt() {
        // dbus-java logs through SLF4J; keep its routine chatter out of the terminal.
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", System.getenv("OPENMYND_DBUS_LOG") ?: "warn")
        setWmClass()
    }

    /**
     * X11 (and XWayland) task managers group windows by WM_CLASS, which AWT derives from the
     * main class name. Set it to the name the .desktop file announces so Plasma shows the right
     * icon and pins/launchers match. Needs `--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED`.
     */
    private fun setWmClass() {
        runCatching {
            val toolkit = Toolkit.getDefaultToolkit()
            if (toolkit.javaClass.name != "sun.awt.X11.XToolkit") return
            toolkit.javaClass.getDeclaredField("awtAppClassName").apply {
                isAccessible = true
                set(toolkit, WM_CLASS)
            }
        }.onFailure { logger.d { "Could not set WM_CLASS: ${it.message}" } }
    }

    /**
     * Extra UI scale to apply inside Compose when the desktop scales but AWT doesn't know about
     * it. The common case is KDE Plasma on Wayland with "Apply scaling themselves" for XWayland
     * apps (or Plasma X11 with a fractional scale): the toolkit is expected to read `Xft.dpi`,
     * which the JDK ignores. Returns 1.0 when the JDK already scales (GDK_SCALE or
     * `-Dsun.java2d.uiScale`). Override with `OPENMYND_SCALE=1.5`.
     */
    fun extraUiScale(): Float {
        System.getenv("OPENMYND_SCALE")?.toFloatOrNull()?.takeIf { it in 0.5f..4f }?.let { return it }
        if (GraphicsEnvironment.isHeadless()) return 1f
        val awtScale = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
        }.getOrDefault(1.0)
        if (awtScale > 1.0) return 1f
        val dpi = xftDpi() ?: return 1f
        val scale = (dpi / 96f)
        return if (scale > 1.05f) scale else 1f
    }

    private fun xftDpi(): Float? = runCatching {
        val process = ProcessBuilder("xrdb", "-query").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(2, TimeUnit.SECONDS)
        parseXftDpi(output)
    }.getOrNull()

    internal fun parseXftDpi(xrdbOutput: String): Float? = xrdbOutput.lineSequence()
        .firstOrNull { it.startsWith("Xft.dpi:") }
        ?.substringAfter(':')?.trim()?.toFloatOrNull()
}
