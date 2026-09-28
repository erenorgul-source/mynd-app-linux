package de.teufel.openmynd.modules.core.bluetooth.bluez

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.ObjectManager
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * State of the local Bluetooth stack, as seen through BlueZ.
 */
sealed interface BlueZState {
    data object Initializing : BlueZState
    /** The system bus or the `org.bluez` service (bluetoothd) is not reachable. */
    data class Unavailable(val reason: String) : BlueZState
    data object NoAdapter : BlueZState
    /** The adapter is soft/hard blocked, e.g. by rfkill or airplane mode. */
    data class Blocked(val adapterPath: String) : BlueZState
    data class PoweredOff(val adapterPath: String) : BlueZState
    data class Ready(val adapterPath: String) : BlueZState
}

/** A GATT characteristic exported by BlueZ for a connected device. */
internal data class GattCharacteristicInfo(
    val path: String,
    val serviceUuid: String,
    val uuid: String,
)

/**
 * Connection to BlueZ on the D-Bus system bus.
 *
 * Mirrors the BlueZ object tree (`org.freedesktop.DBus.ObjectManager` at `/`) in memory and
 * keeps it current from `InterfacesAdded`, `InterfacesRemoved` and `PropertiesChanged`
 * signals. Listeners are invoked on dbus-java's single signal thread, so they observe
 * events in the order BlueZ emitted them (important for fragmented notifications).
 */
class BlueZClient(
    private val connectionFactory: () -> DBusConnection = {
        DBusConnectionBuilder.forSystemBus().withShared(false).build()
    },
) : AutoCloseable {

    /** Callbacks for BlueZ object changes. Invoked after the cache has been updated. */
    interface Listener {
        fun onInterfacesAdded(path: String, interfaces: Set<String>) {}
        fun onInterfacesRemoved(path: String, interfaces: List<String>) {}
        fun onPropertiesChanged(path: String, iface: String, changed: Map<String, Any?>) {}
    }

    private val logger = Logger.withTag("BlueZ")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<Listener>()

    /** path -> interface -> property -> unwrapped value */
    private val objects = mutableMapOf<String, MutableMap<String, MutableMap<String, Any?>>>()

    @Volatile
    private var connection: DBusConnection? = null
    private val signalHandlers = mutableListOf<AutoCloseable>()

    private val _state = MutableStateFlow<BlueZState>(BlueZState.Initializing)
    val state: StateFlow<BlueZState> = _state.asStateFlow()

    private val _revision = MutableStateFlow(0L)

    /** Incremented on every change to the mirrored object tree. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun addListener(listener: Listener) {
        listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    /**
     * Connects to the system bus and loads the BlueZ object tree in the background.
     * Safe to call again (e.g. from a "retry" button) to reconnect after a failure.
     */
    fun start() {
        scope.launch {
            if (connection != null) reload() else connect()
        }
    }

    private fun connect() {
        synchronized(lock) {
            if (connection != null) return
            _state.value = BlueZState.Initializing
        }
        try {
            val conn = connectionFactory()
            synchronized(lock) { connection = conn }
            registerSignalHandlers(conn)
            reload()
        } catch (e: Exception) {
            logger.w(e) { "Cannot connect to BlueZ" }
            close()
            _state.value = BlueZState.Unavailable(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    private fun registerSignalHandlers(conn: DBusConnection) {
        // Register handlers before GetManagedObjects so no change can slip in between.
        signalHandlers += conn.addSigHandler(ObjectManager.InterfacesAdded::class.java) { signal ->
            val path = signal.signalSource.path
            if (!path.startsWith(BlueZ.ROOT_PATH)) return@addSigHandler
            synchronized(lock) {
                val ifaces = objects.getOrPut(path) { mutableMapOf() }
                signal.interfaces.forEach { (iface, props) -> ifaces[iface] = props.unwrapValues() }
            }
            onTreeChanged()
            listeners.forEach { it.onInterfacesAdded(path, signal.interfaces.keys) }
        }
        signalHandlers += conn.addSigHandler(ObjectManager.InterfacesRemoved::class.java) { signal ->
            val path = signal.signalSource.path
            if (!path.startsWith(BlueZ.ROOT_PATH)) return@addSigHandler
            synchronized(lock) {
                objects[path]?.let { ifaces ->
                    signal.interfaces.forEach { ifaces.remove(it) }
                    if (ifaces.isEmpty()) objects.remove(path)
                }
            }
            onTreeChanged()
            listeners.forEach { it.onInterfacesRemoved(path, signal.interfaces) }
        }
        signalHandlers += conn.addSigHandler(Properties.PropertiesChanged::class.java) { signal ->
            val path = signal.path
            if (!path.startsWith(BlueZ.ROOT_PATH)) return@addSigHandler
            val changed = signal.propertiesChanged.unwrapValues()
            synchronized(lock) {
                val props = objects.getOrPut(path) { mutableMapOf() }.getOrPut(signal.interfaceName) { mutableMapOf() }
                props.putAll(changed)
                signal.propertiesRemoved.forEach { props.remove(it) }
            }
            onTreeChanged()
            listeners.forEach { it.onPropertiesChanged(path, signal.interfaceName, changed) }
        }
        // bluetoothd restarting (or being started after the app) changes the owner of org.bluez.
        signalHandlers += conn.addSigHandler(DBus.NameOwnerChanged::class.java) { signal ->
            if (signal.name != BlueZ.BUS_NAME) return@addSigHandler
            logger.i { "org.bluez owner changed: '${signal.oldOwner}' -> '${signal.newOwner}'" }
            scope.launch { runCatching { reload() }.onFailure { logger.w(it) { "Reload failed" } } }
        }
    }

    private fun reload() {
        val conn = connection ?: return
        val managed = try {
            conn.getRemoteObject(BlueZ.BUS_NAME, "/", ObjectManager::class.java).GetManagedObjects()
        } catch (e: Exception) {
            logger.w { "BlueZ not available: ${e.message}" }
            synchronized(lock) { objects.clear() }
            onTreeChanged()
            _state.value = BlueZState.Unavailable(e.message ?: "org.bluez is not running")
            return
        }
        synchronized(lock) {
            objects.clear()
            managed.forEach { (path, ifaces) ->
                if (!path.path.startsWith(BlueZ.ROOT_PATH)) return@forEach
                objects[path.path] = ifaces.mapValuesTo(mutableMapOf()) { (_, props) -> props.unwrapValues() }
            }
        }
        onTreeChanged()
    }

    private fun onTreeChanged() {
        _revision.value++
        if (connection == null) return
        val current = _state.value
        if (current is BlueZState.Unavailable && objects.isEmpty()) return
        _state.value = computeAdapterState()
    }

    private fun computeAdapterState(): BlueZState {
        val adapterPath = adapterPath() ?: return BlueZState.NoAdapter
        val props = properties(adapterPath, BlueZ.ADAPTER)
        return when {
            props["PowerState"] == "off-blocked" -> BlueZState.Blocked(adapterPath)
            props["Powered"] == true -> BlueZState.Ready(adapterPath)
            else -> BlueZState.PoweredOff(adapterPath)
        }
    }

    /** The adapter to use: the first powered one, else the first one (usually hci0). */
    fun adapterPath(): String? = synchronized(lock) {
        val adapters = objects.filterValues { BlueZ.ADAPTER in it }.keys.sorted()
        adapters.firstOrNull { objects[it]?.get(BlueZ.ADAPTER)?.get("Powered") == true } ?: adapters.firstOrNull()
    }

    /** Snapshot of the properties of [iface] on [path], empty if unknown. */
    fun properties(path: String, iface: String): Map<String, Any?> = synchronized(lock) {
        objects[path]?.get(iface)?.toMap().orEmpty()
    }

    fun hasInterface(path: String, iface: String): Boolean = synchronized(lock) {
        objects[path]?.containsKey(iface) == true
    }

    /** Paths of all objects implementing [iface] whose path starts with [prefix]. */
    fun pathsWithInterface(iface: String, prefix: String = BlueZ.ROOT_PATH): List<String> = synchronized(lock) {
        objects.filter { (path, ifaces) -> path.startsWith(prefix) && iface in ifaces }.keys.sorted()
    }

    /** Suspends until [predicate] holds for the property's cached value. */
    suspend fun awaitProperty(path: String, iface: String, name: String, predicate: (Any?) -> Boolean) {
        revision.first { predicate(properties(path, iface)[name]) }
    }

    /** GATT characteristics BlueZ has resolved for the device at [devicePath]. */
    internal fun characteristicsOf(devicePath: String): List<GattCharacteristicInfo> = synchronized(lock) {
        objects.filter { (path, ifaces) -> path.startsWith("$devicePath/") && BlueZ.GATT_CHARACTERISTIC in ifaces }
            .mapNotNull { (path, ifaces) ->
                val props = ifaces.getValue(BlueZ.GATT_CHARACTERISTIC)
                val uuid = props["UUID"] as? String ?: return@mapNotNull null
                val servicePath = props["Service"].asObjectPath() ?: return@mapNotNull null
                val serviceUuid = objects[servicePath]?.get(BlueZ.GATT_SERVICE)?.get("UUID") as? String
                    ?: return@mapNotNull null
                GattCharacteristicInfo(path, serviceUuid.lowercase(), uuid.lowercase())
            }
    }

    internal fun adapter(path: String): Adapter1 = remote(path, Adapter1::class.java)
    internal fun device(path: String): Device1 = remote(path, Device1::class.java)
    internal fun characteristic(path: String): GattCharacteristic1 = remote(path, GattCharacteristic1::class.java)

    /** Sets a BlueZ property, e.g. `Adapter1.Powered`. Blocking D-Bus call. */
    fun setProperty(path: String, iface: String, name: String, value: Any) {
        remote(path, Properties::class.java).Set(iface, name, value)
    }

    /** Powers the current adapter on or off. */
    suspend fun setAdapterPowered(powered: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val path = adapterPath() ?: error("No Bluetooth adapter")
            setProperty(path, BlueZ.ADAPTER, "Powered", powered)
        }.onFailure { logger.w(it) { "Failed to set Powered=$powered" } }
    }

    private fun <T : DBusInterface> remote(path: String, type: Class<T>): T {
        val conn = connection ?: error("Not connected to BlueZ")
        return conn.getRemoteObject(BlueZ.BUS_NAME, path, type)
    }

    override fun close() {
        synchronized(lock) {
            signalHandlers.forEach { runCatching { it.close() } }
            signalHandlers.clear()
            connection?.let { runCatching { it.close() } }
            connection = null
        }
    }
}

private fun Map<String, Variant<*>>.unwrapValues(): MutableMap<String, Any?> =
    mapValuesTo(mutableMapOf()) { (_, v) -> v.value.unwrapVariant() }

private fun Any?.unwrapVariant(): Any? = if (this is Variant<*>) value.unwrapVariant() else this

internal fun Any?.asObjectPath(): String? = when (this) {
    is DBusPath -> path
    is String -> this
    else -> null
}

/** D-Bus `ay` values may surface as `byte[]` or as a list/array of boxed bytes. */
internal fun Any?.asByteArray(): ByteArray? = when (this) {
    is ByteArray -> this
    is List<*> -> ByteArray(size) { (this[it] as Number).toByte() }
    is Array<*> -> ByteArray(size) { (this[it] as Number).toByte() }
    else -> null
}

internal fun Any?.asStringList(): List<String> = when (this) {
    is List<*> -> filterIsInstance<String>()
    is Array<*> -> filterIsInstance<String>()
    else -> emptyList()
}
