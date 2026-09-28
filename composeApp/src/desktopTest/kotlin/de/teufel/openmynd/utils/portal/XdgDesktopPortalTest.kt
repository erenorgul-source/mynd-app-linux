package de.teufel.openmynd.utils.portal

import de.teufel.openmynd.modules.core.bluetooth.bluez.PrivateDBusDaemon
import de.teufel.openmynd.modules.ui.theme.LinuxThemeDetector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.base.AbstractConnectionBase
import org.freedesktop.dbus.connections.impl.BaseConnectionBuilder
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Signals emitted by the mock portal; generic handlers match them by interface/member. */
class SettingChangedSignal(path: String, namespace: String, key: String, value: Variant<*>) : DBusSignal(
    BaseConnectionBuilder.getSystemEndianness(), null, path,
    XdgDesktopPortal.SETTINGS_INTERFACE, "SettingChanged", "ssv", namespace, key, value,
)

class ResponseSignal(path: String, code: UInt32, results: Map<String, Variant<*>>) : DBusSignal(
    BaseConnectionBuilder.getSystemEndianness(), null, path,
    XdgDesktopPortal.REQUEST_INTERFACE, "Response", "ua{sv}", code, results,
)

class XdgDesktopPortalTest {
    private lateinit var daemon: PrivateDBusDaemon
    private lateinit var portalConnection: DBusConnection
    private lateinit var portal: XdgDesktopPortal

    @Volatile
    private var colorScheme = 1

    @Volatile
    private var fileResponse: Pair<Int, String?> = 0 to "file:///tmp/My%20Firmware.bin"

    @BeforeTest
    fun setUp() {
        assumeTrue("dbus-daemon not installed", PrivateDBusDaemon.isAvailable)
        daemon = PrivateDBusDaemon()
        portalConnection = daemon.connect()
        portalConnection.requestBusName(XdgDesktopPortal.BUS_NAME)
        portalConnection.exportObject(XdgDesktopPortal.OBJECT_PATH, MockPortal())
        portal = XdgDesktopPortal { daemon.connect() }
    }

    @AfterTest
    fun tearDown() {
        // Close whatever setUp managed to create, even if it failed halfway.
        if (::portal.isInitialized) portal.close()
        if (::portalConnection.isInitialized) portalConnection.close()
        if (::daemon.isInitialized) daemon.close()
    }

    private inner class MockPortal : PortalSettings, PortalFileChooser {
        override fun getObjectPath() = XdgDesktopPortal.OBJECT_PATH

        override fun readOne(namespace: String, key: String): Variant<*> = Variant(UInt32(colorScheme.toLong()))

        override fun read(namespace: String, key: String): Variant<*> = Variant(Variant(UInt32(colorScheme.toLong())))

        override fun openFile(parentWindow: String, title: String, options: Map<String, Variant<*>>): DBusPath {
            val token = options.getValue("handle_token").value as String
            val sender = AbstractConnectionBase.getCallInfo().source.removePrefix(":").replace('.', '_')
            val handle = "${XdgDesktopPortal.OBJECT_PATH}/request/$sender/$token"
            Thread {
                Thread.sleep(100) // the user picking a file
                val (code, uri) = fileResponse
                val results = if (uri != null) mapOf<String, Variant<*>>("uris" to Variant(arrayOf(uri))) else emptyMap()
                portalConnection.sendMessage(ResponseSignal(handle, UInt32(code.toLong()), results))
            }.start()
            return DBusPath(handle)
        }
    }

    @Test
    fun readsColorScheme() {
        assertEquals(1, portal.readColorScheme())
        colorScheme = 2
        assertEquals(2, portal.readColorScheme())
    }

    @Test
    fun followsColorSchemeChanges() = runBlocking {
        val changed = CompletableDeferred<Int>()
        portal.watchColorScheme { changed.complete(it) }
        portalConnection.sendMessage(
            SettingChangedSignal(
                XdgDesktopPortal.OBJECT_PATH,
                XdgDesktopPortal.APPEARANCE_NAMESPACE,
                XdgDesktopPortal.COLOR_SCHEME_KEY,
                Variant(UInt32(2)),
            )
        )
        assertEquals(2, withTimeout(5_000) { changed.await() })
    }

    @Test
    fun openFileReturnsSelection() = runBlocking {
        val result = withTimeout(5_000) { portal.openFile("Pick") }
        assertEquals(XdgDesktopPortal.FileResult.Selected(File("/tmp/My Firmware.bin")), result)
    }

    @Test
    fun openFileCancelled() = runBlocking {
        fileResponse = 1 to null
        assertEquals(XdgDesktopPortal.FileResult.Cancelled, withTimeout(5_000) { portal.openFile("Pick") })
    }

    @Test
    fun unavailableWithoutPortal() = runBlocking {
        portalConnection.releaseBusName(XdgDesktopPortal.BUS_NAME)
        assertNull(portal.readColorScheme())
        assertEquals(XdgDesktopPortal.FileResult.Unavailable, withTimeout(5_000) { portal.openFile("Pick") })
    }

    @Test
    fun themeDetectorPrefersOverrideThenPortal() {
        val noKde = File("/nonexistent/kdeglobals")
        assertEquals(false, LinuxThemeDetector(portal, noKde) { if (it == "OPENMYND_THEME") "light" else null }.isDark())
        assertEquals(true, LinuxThemeDetector(portal, noKde) { null }.isDark())
    }
}

class LinuxThemeDetectorTest {
    private fun kdeGlobals(content: String) = File.createTempFile("kdeglobals", "").apply {
        deleteOnExit()
        writeText(content)
    }

    @Test
    fun breezeDarkIsDark() {
        val file = kdeGlobals("[General]\nColorScheme=BreezeDark\n\n[Colors:View]\nBackgroundNormal=255,255,255\n\n[Colors:Window]\nBackgroundNormal=32,35,38\n")
        assertEquals(true, LinuxThemeDetector.isKdeColorSchemeDark(file))
    }

    @Test
    fun breezeLightIsLight() {
        val file = kdeGlobals("[Colors:Window]\nBackgroundForeground=35,38,41\nBackgroundNormal=239,240,241\n")
        assertEquals(false, LinuxThemeDetector.isKdeColorSchemeDark(file))
    }

    @Test
    fun unknownWithoutColors() {
        assertNull(LinuxThemeDetector.isKdeColorSchemeDark(kdeGlobals("[General]\nfont=Noto Sans\n")))
        assertNull(LinuxThemeDetector.isKdeColorSchemeDark(File("/nonexistent/kdeglobals")))
    }
}
