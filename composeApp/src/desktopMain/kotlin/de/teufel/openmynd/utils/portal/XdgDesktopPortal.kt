package de.teufel.openmynd.utils.portal

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.matchrules.DBusMatchRule
import org.freedesktop.dbus.matchrules.DBusMatchRuleBuilder
import org.freedesktop.dbus.types.Variant
import java.io.File
import java.net.URI
import kotlin.random.Random

@DBusInterfaceName(XdgDesktopPortal.SETTINGS_INTERFACE)
internal interface PortalSettings : DBusInterface {
    /** Portal version 2+, returns the value directly. */
    @DBusMemberName("ReadOne")
    fun readOne(namespace: String, key: String): Variant<*>

    /** Deprecated but universally available; returns the value wrapped in another variant. */
    @DBusMemberName("Read")
    fun read(namespace: String, key: String): Variant<*>
}

@DBusInterfaceName(XdgDesktopPortal.FILE_CHOOSER_INTERFACE)
internal interface PortalFileChooser : DBusInterface {
    @DBusMemberName("OpenFile")
    fun openFile(parentWindow: String, title: String, options: Map<String, Variant<*>>): DBusPath
}

/**
 * Minimal client for the XDG Desktop Portal (`xdg-desktop-portal` + a desktop specific backend
 * such as `xdg-desktop-portal-kde`), used for native file dialogs and the system dark mode
 * preference. Every call degrades gracefully when no portal is running.
 */
class XdgDesktopPortal(
    private val connectionFactory: () -> DBusConnection = {
        DBusConnectionBuilder.forSessionBus().withShared(false).build()
    },
) : AutoCloseable {

    sealed interface FileResult {
        data class Selected(val file: File) : FileResult
        data object Cancelled : FileResult
        data object Unavailable : FileResult
    }

    private val logger = Logger.withTag("XdgPortal")

    private val connection: DBusConnection? by lazy {
        runCatching(connectionFactory).onFailure { logger.w { "No D-Bus session bus: ${it.message}" } }.getOrNull()
    }

    /**
     * `org.freedesktop.appearance color-scheme`: 0 = no preference, 1 = dark, 2 = light.
     * Null when the portal or the setting is not available. Blocking.
     */
    fun readColorScheme(): Int? {
        val conn = connection ?: return null
        return runCatching {
            val settings = conn.getRemoteObject(BUS_NAME, OBJECT_PATH, PortalSettings::class.java)
            val value = runCatching { settings.readOne(APPEARANCE_NAMESPACE, COLOR_SCHEME_KEY) }
                .getOrElse { settings.read(APPEARANCE_NAMESPACE, COLOR_SCHEME_KEY) }
            (value.unwrap() as? Number)?.toInt()
        }.onFailure { logger.d { "color-scheme not available: ${it.message}" } }.getOrNull()
    }

    /** Invokes [onChanged] with the new color-scheme value whenever it changes. */
    fun watchColorScheme(onChanged: (Int) -> Unit): AutoCloseable? {
        val conn = connection ?: return null
        return runCatching {
            conn.addGenericSigHandler(signalRule(SETTINGS_INTERFACE, "SettingChanged", OBJECT_PATH)) { signal ->
                val args = signal.parameters
                if (args.getOrNull(0) == APPEARANCE_NAMESPACE && args.getOrNull(1) == COLOR_SCHEME_KEY) {
                    (args.getOrNull(2).unwrap() as? Number)?.toInt()?.let(onChanged)
                }
            }
        }.onFailure { logger.d { "Cannot watch color-scheme: ${it.message}" } }.getOrNull()
    }

    /** Shows the desktop's native "open file" dialog and suspends until it is closed. */
    suspend fun openFile(title: String): FileResult = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext FileResult.Unavailable
        val token = "openmynd${Random.nextInt(0, Int.MAX_VALUE)}"
        val sender = conn.uniqueName.removePrefix(":").replace('.', '_')
        val expectedHandle = "$OBJECT_PATH/request/$sender/$token"
        val response = CompletableDeferred<Pair<Int, Map<*, *>>>()
        val handlers = mutableListOf<AutoCloseable>()

        fun subscribe(handle: String) {
            handlers += conn.addGenericSigHandler(signalRule(REQUEST_INTERFACE, "Response", handle)) { signal ->
                val args = signal.parameters
                val code = (args.getOrNull(0) as? Number)?.toInt() ?: 2
                response.complete(code to (args.getOrNull(1) as? Map<*, *>).orEmpty())
            }
        }

        try {
            // Subscribe before calling so a fast response can't be missed.
            subscribe(expectedHandle)
            val chooser = conn.getRemoteObject(BUS_NAME, OBJECT_PATH, PortalFileChooser::class.java)
            val handle = chooser.openFile(
                "",
                title,
                mapOf("handle_token" to Variant(token), "modal" to Variant(true)),
            ).path
            // Very old portals ignore handle_token and pick their own request path.
            if (handle != expectedHandle) subscribe(handle)

            val (code, results) = response.await()
            if (code != 0) return@withContext FileResult.Cancelled
            val uri = results["uris"].unwrap().let { uris ->
                when (uris) {
                    is List<*> -> uris.firstOrNull()
                    is Array<*> -> uris.firstOrNull()
                    else -> null
                }
            } as? String ?: return@withContext FileResult.Cancelled
            FileResult.Selected(File(URI(uri)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.i { "File chooser portal not available: ${e.message}" }
            FileResult.Unavailable
        } finally {
            handlers.forEach { runCatching { it.close() } }
        }
    }

    override fun close() {
        runCatching { connection?.close() }
    }

    companion object {
        const val BUS_NAME = "org.freedesktop.portal.Desktop"
        const val OBJECT_PATH = "/org/freedesktop/portal/desktop"
        const val SETTINGS_INTERFACE = "org.freedesktop.portal.Settings"
        const val FILE_CHOOSER_INTERFACE = "org.freedesktop.portal.FileChooser"
        const val REQUEST_INTERFACE = "org.freedesktop.portal.Request"
        const val APPEARANCE_NAMESPACE = "org.freedesktop.appearance"
        const val COLOR_SCHEME_KEY = "color-scheme"
    }
}

private fun signalRule(iface: String, member: String, path: String): DBusMatchRule =
    DBusMatchRuleBuilder.create().withType("signal").withInterface(iface).withMember(member).withPath(path).build()

private fun Any?.unwrap(): Any? = if (this is Variant<*>) value.unwrap() else this
