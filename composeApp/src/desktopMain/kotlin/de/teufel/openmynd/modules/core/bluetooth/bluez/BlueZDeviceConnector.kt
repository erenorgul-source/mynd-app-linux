package de.teufel.openmynd.modules.core.bluetooth.bluez

import co.touchlab.kermit.Logger
import de.teufel.openmynd.modules.core.bluetooth.connector.DeviceConnector
import de.teufel.openmynd.modules.core.bluetooth.model.BluetoothDevice
import de.teufel.openmynd.modules.core.device.findDeviceTypeByName
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol.Companion.ACTIONS_COMMAND_CHARACTERISTIC_UUID
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol.Companion.ACTIONS_RESPONSE_CHARACTERISTIC_UUID
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol.Companion.ACTIONS_SERVICE_UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bluez.Error as BlueZError
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.ConcurrentHashMap

/**
 * [DeviceConnector] for Linux, talking to BlueZ (bluetoothd) over D-Bus.
 *
 * Differences to the mobile backends worth knowing:
 * - Device ids are Bluetooth addresses, so known devices survive restarts like on Android.
 * - BlueZ negotiates the ATT MTU itself when connecting (up to 517 by default), there is no
 *   client API to request it; [requestMtu] only reports the negotiated value.
 * - `StartNotify` replies once the CCCD write has completed, which gives
 *   [subscribeToCharacteristic] its "notifications are live" guarantee without extra waiting.
 * - Pairing is not forced. If the speaker requires it, BlueZ raises the link security when a
 *   characteristic reports insufficient authentication, and the desktop's Bluetooth agent
 *   (KDE BlueDevil, GNOME Settings, blueman, ...) handles any confirmation.
 */
class BlueZDeviceConnector(
    private val bluez: BlueZClient,
) : DeviceConnector, BlueZClient.Listener {

    companion object {
        private const val SCAN_TIMEOUT_MS = 10_000L
        private const val SERVICES_RESOLVED_TIMEOUT_MS = 20_000L
        private const val DISCONNECT_TIMEOUT_MS = 3_000L
        private const val CHARACTERISTIC_FLOW_BUFFER = 256
        private const val DEBUG_MODE = false

        private val WRITE_WITH_RESPONSE = mapOf<String, Variant<*>>("type" to Variant("request"))
        private val WRITE_WITHOUT_RESPONSE = mapOf<String, Variant<*>>("type" to Variant("command"))
    }

    private val logger = Logger.withTag("BlueZConnector")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    override val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** Bluetooth address -> BlueZ device object path. */
    private val devicePathById = ConcurrentHashMap<String, String>()

    @Volatile
    private var connectedPath: String? = null

    /** "serviceUuid|characteristicUuid" (lowercase) -> characteristic object path. */
    @Volatile
    private var characteristicPaths: Map<String, String> = emptyMap()

    /** Characteristic object path -> notification values. */
    private val characteristicUpdates = ConcurrentHashMap<String, MutableSharedFlow<ByteArray>>()

    private var scanTimeoutJob: Job? = null
    private var gattSetupJob: Job? = null
    private val discoveryMutex = Mutex()
    private var discoveryActive = false
    private val gattWriteMutex = Mutex()

    init {
        bluez.addListener(this)
    }

    // region Scanning

    override fun startScan() {
        if (_isScanning.value) return
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            setDiscovery(true)
            delay(SCAN_TIMEOUT_MS)
            if (_isScanning.value) stopScan()
        }
    }

    override fun stopScan() {
        if (!_isScanning.value) return
        _isScanning.value = false
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        scope.launch { setDiscovery(false) }
    }

    /**
     * Starts/stops BlueZ discovery. Serialized so a quick start/stop sequence can't leave
     * discovery running: a start that runs after [stopScan] has flipped [isScanning] is skipped.
     */
    private suspend fun setDiscovery(active: Boolean) = discoveryMutex.withLock {
        if (active && !_isScanning.value) return@withLock
        if (active == discoveryActive) return@withLock
        val adapterPath = bluez.adapterPath() ?: run {
            logger.w { "No Bluetooth adapter" }
            _isScanning.value = false
            return@withLock
        }
        try {
            val adapter = bluez.adapter(adapterPath)
            if (active) {
                adapter.setDiscoveryFilter(
                    mapOf(
                        "Transport" to Variant("le"),
                        // Report every advertisement so RSSI stays current while scanning.
                        "DuplicateData" to Variant(true),
                    )
                )
                adapter.startDiscovery()
            } else {
                adapter.stopDiscovery()
            }
            discoveryActive = active
        } catch (_: BlueZError.InProgress) {
            // Discovery already running (possibly started by another client).
            discoveryActive = active
        } catch (e: Exception) {
            logger.w { "${if (active) "StartDiscovery" else "StopDiscovery"} failed: ${e.message}" }
            discoveryActive = false
            if (active) _isScanning.value = false
        }
    }

    private fun onDeviceSeen(path: String) {
        val adapterPath = bluez.adapterPath() ?: return
        if (!path.startsWith("$adapterPath/")) return
        val device = deviceFromProperties(bluez.properties(path, BlueZ.DEVICE)) ?: return
        if (!isSupportedDevice(device)) return
        devicePathById[device.id] = path
        val current = _discoveredDevices.value.toMutableList()
        val index = current.indexOfFirst { it.id == device.id }
        if (index >= 0) current[index] = device else current.add(device)
        _discoveredDevices.value = current.sortedByDescending { it.rssi }
    }

    private fun deviceFromProperties(props: Map<String, Any?>): BluetoothDevice? {
        val address = props["Address"] as? String ?: return null
        val name = props["Name"] as? String ?: props["Alias"] as? String ?: "Unknown Device"
        return BluetoothDevice(
            id = address,
            name = name,
            rssi = (props["RSSI"] as? Number)?.toInt() ?: -100,
            // BlueZ only reports connectable LE devices as discovered objects.
            isConnectable = true,
        )
    }

    @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
    private fun isSupportedDevice(device: BluetoothDevice): Boolean {
        return DEBUG_MODE || findDeviceTypeByName(device.name) != null
    }

    // endregion

    // region BlueZ events

    override fun onInterfacesAdded(path: String, interfaces: Set<String>) {
        if (BlueZ.DEVICE in interfaces && _isScanning.value) onDeviceSeen(path)
    }

    override fun onInterfacesRemoved(path: String, interfaces: List<String>) {
        if (BlueZ.DEVICE !in interfaces) return
        _discoveredDevices.value.firstOrNull { devicePathById[it.id] == path }?.let { gone ->
            _discoveredDevices.value = _discoveredDevices.value - gone
        }
        if (path == connectedPath) onDisconnected()
    }

    override fun onPropertiesChanged(path: String, iface: String, changed: Map<String, Any?>) {
        when (iface) {
            BlueZ.GATT_CHARACTERISTIC -> {
                // Called on the single dbus-java signal thread: tryEmit keeps fragments in
                // arrival order and never blocks delivery of later events.
                val value = changed["Value"].asByteArray() ?: return
                characteristicUpdates[path]?.tryEmit(value)
            }
            BlueZ.DEVICE -> {
                if (_isScanning.value && ("RSSI" in changed || "ManufacturerData" in changed || "Name" in changed)) {
                    onDeviceSeen(path)
                }
                if (path == connectedPath) {
                    if (changed["Connected"] == false) onDisconnected()
                    if (changed["ServicesResolved"] == false) _isReady.value = false
                }
            }
        }
    }

    private fun onDisconnected() {
        logger.i { "Device disconnected: $connectedPath" }
        gattSetupJob?.cancel()
        connectedPath = null
        characteristicPaths = emptyMap()
        _isReady.value = false
        _connectedDevice.value = null
    }

    // endregion

    // region Connection

    override suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        val path = devicePathById[device.id] ?: findDevicePath(device.id) ?: run {
            logger.w { "Unknown device ${device.id}" }
            return@withContext false
        }
        if (connectedPath == path && _connectedDevice.value != null) return@withContext true
        if (_connectedDevice.value != null) {
            disconnect()
            delay(500)
        }
        // Discovery running in parallel makes LE connection attempts much less reliable.
        stopScan()
        setDiscovery(false)

        try {
            bluez.device(path).connect()
        } catch (_: BlueZError.AlreadyConnected) {
            // Fine, e.g. connected from the desktop's Bluetooth settings.
        } catch (e: Exception) {
            logger.w(e) { "Connect to ${device.id} failed" }
            return@withContext false
        }

        connectedPath = path
        _isReady.value = false
        _connectedDevice.value = deviceFromProperties(bluez.properties(path, BlueZ.DEVICE)) ?: device
        gattSetupJob?.cancel()
        gattSetupJob = scope.launch { setUpGatt(path) }
        true
    }

    private fun findDevicePath(id: String): String? {
        val adapterPath = bluez.adapterPath() ?: return null
        return bluez.pathsWithInterface(BlueZ.DEVICE, "$adapterPath/")
            .firstOrNull { bluez.properties(it, BlueZ.DEVICE)["Address"] == id }
            ?.also { devicePathById[id] = it }
    }

    /** Waits for BlueZ to resolve the GATT database and maps the characteristics. */
    private suspend fun setUpGatt(path: String) {
        val resolved = withTimeoutOrNull(SERVICES_RESOLVED_TIMEOUT_MS) {
            bluez.awaitProperty(path, BlueZ.DEVICE, "ServicesResolved") { it == true || connectedPath != path }
        } != null
        if (connectedPath != path) return
        if (!resolved) {
            logger.w { "GATT services of $path not resolved in time" }
            return
        }
        val characteristics = bluez.characteristicsOf(path)
        characteristicPaths = characteristics.associate { "${it.serviceUuid}|${it.uuid}" to it.path }
        logger.d { "Resolved ${characteristics.size} characteristics" }
        _isReady.value = findCharacteristicPath(ACTIONS_SERVICE_UUID, ACTIONS_COMMAND_CHARACTERISTIC_UUID) != null &&
            findCharacteristicPath(ACTIONS_SERVICE_UUID, ACTIONS_RESPONSE_CHARACTERISTIC_UUID) != null
        if (!_isReady.value) logger.w { "Device doesn't expose the Actions service" }
    }

    override suspend fun disconnect(): Boolean = withContext(Dispatchers.IO) {
        val path = connectedPath ?: return@withContext true
        try {
            bluez.device(path).disconnect()
            withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) { _connectedDevice.first { it == null } }
            if (_connectedDevice.value != null) onDisconnected()
            true
        } catch (e: Exception) {
            logger.w(e) { "Disconnect failed" }
            onDisconnected()
            false
        }
    }

    // endregion

    // region GATT

    private fun findCharacteristicPath(serviceUuid: String, characteristicUuid: String): String? =
        characteristicPaths["${serviceUuid.lowercase()}|${characteristicUuid.lowercase()}"]

    private fun requireCharacteristicPath(serviceUuid: String, characteristicUuid: String): String {
        check(connectedPath != null) { "Not connected" }
        return findCharacteristicPath(serviceUuid, characteristicUuid)
            ?: throw NoSuchElementException("Characteristic not found: $characteristicUuid")
    }

    override suspend fun readCharacteristic(serviceUuid: String, characteristicUuid: String): Flow<ByteArray> = flow {
        val path = requireCharacteristicPath(serviceUuid, characteristicUuid)
        val value = try {
            withContext(Dispatchers.IO) { bluez.characteristic(path).readValue(emptyMap()) }
        } catch (e: Exception) {
            logger.w { "Read $characteristicUuid failed: ${e.message}" }
            ByteArray(0)
        }
        emit(value)
    }

    override suspend fun writeCharacteristic(serviceUuid: String, characteristicUuid: String, data: ByteArray): Boolean =
        write(serviceUuid, characteristicUuid, data, WRITE_WITH_RESPONSE)

    override suspend fun writeCharacteristicWithoutResponse(
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray,
    ): Boolean = write(serviceUuid, characteristicUuid, data, WRITE_WITHOUT_RESPONSE)

    private suspend fun write(
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray,
        options: Map<String, Variant<*>>,
    ): Boolean = gattWriteMutex.withLock {
        withContext(Dispatchers.IO) {
            val path = findCharacteristicPath(serviceUuid, characteristicUuid) ?: return@withContext false
            try {
                // "request" returns after the peer's ATT write response, "command" once queued.
                bluez.characteristic(path).writeValue(data, options)
                true
            } catch (e: Exception) {
                logger.w { "Write $characteristicUuid failed: ${e.message}" }
                false
            }
        }
    }

    override suspend fun subscribeToCharacteristic(serviceUuid: String, characteristicUuid: String): Flow<ByteArray> {
        val path = requireCharacteristicPath(serviceUuid, characteristicUuid)
        val devicePath = connectedPath
        // Create the flow before enabling notifications so the first ones can't be missed.
        val updates = characteristicUpdates.getOrPut(path) {
            MutableSharedFlow(extraBufferCapacity = CHARACTERISTIC_FLOW_BUFFER)
        }
        withContext(Dispatchers.IO) {
            try {
                // BlueZ replies only after the CCCD write has been acknowledged by the peer.
                bluez.characteristic(path).startNotify()
            } catch (_: BlueZError.InProgress) {
                // A StartNotify for this characteristic is already pending.
            }
        }
        return flow {
            try {
                updates.collect { emit(it) }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    if (connectedPath == devicePath) {
                        runCatching { bluez.characteristic(path).stopNotify() }
                    }
                }
            }
        }
    }

    override suspend fun requestMtu(mtuSize: Int): Boolean {
        if (connectedPath == null) return false
        // BlueZ >= 5.62 exposes the negotiated ATT MTU on every characteristic.
        val mtu = characteristicPaths.values.firstNotNullOfOrNull {
            (bluez.properties(it, BlueZ.GATT_CHARACTERISTIC)["MTU"] as? Number)?.toInt()
        }
        logger.i { "ATT MTU negotiated by BlueZ: ${mtu ?: "unknown"} (requested $mtuSize)" }
        return true
    }

    // endregion
}
