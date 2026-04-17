package de.teufel.openmynd.modules.core.bluetooth.connector

import de.teufel.openmynd.modules.core.bluetooth.model.BluetoothDevice
import de.teufel.openmynd.modules.core.bluetooth.util.characteristicUuidIfCccdDescriptorWrite
import de.teufel.openmynd.modules.core.device.findDeviceTypeByName
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol.Companion.ACTIONS_SERVICE_UUID
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol.Companion.ACTIONS_COMMAND_CHARACTERISTIC_UUID
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol.Companion.ACTIONS_RESPONSE_CHARACTERISTIC_UUID
import dev.bluefalcon.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
class BlueFalconDeviceConnector(
    val blueFalcon: BlueFalcon
) : DeviceConnector {

    companion object {
        private const val WRITE_TYPE_DEFAULT = 2
        private const val WRITE_TYPE_NO_RESPONSE = 1
        private const val WRITE_NO_RESPONSE_THROTTLE_MS = 8L
        private const val SCAN_TIMEOUT_MS = 5000L
        private const val DEBUG_MODE = false
        // Sized to absorb iOS notification bursts (battery + volume + sources + sound icons
        // can all fire within the same connection interval). Delivery uses tryEmit so the
        // single consumer of [characteristicEventQueue] never suspends on a full SharedFlow
        // buffer — a suspending emit would stall the whole BLE→protocol pipeline and freeze
        // notifications/ACKs until the collector catches up.
        private const val CHARACTERISTIC_FLOW_BUFFER = 256
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    override val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val characteristicUpdates = mutableMapOf<String, MutableSharedFlow<ByteArray>>()
    private val characteristicMutex = Mutex()

    private var scanTimeoutJob: Job? = null

    private val _discoveredServiceCharacteristics = mutableMapOf<String, MutableMap<String, BluetoothCharacteristic>>()
    private val discoveredPeripherals = mutableMapOf<String, BluetoothPeripheral>()
    private val mtuDeferredByPeripheralId = mutableMapOf<String, CompletableDeferred<Boolean>>()
    private val characteristicEventQueue = Channel<Pair<String, ByteArray>>(Channel.UNLIMITED)
    private val gattWriteMutex = Mutex()

    // Tracks pending CCCD (descriptor) writes so subscribeToCharacteristic can wait for the
    // subscription to actually take effect on the peer before anyone starts writing
    // commands. On iOS BlueFalcon's PeripheralDelegate is wired to call
    // didWriteCharacteristic for descriptor writes only; we use that signal here. Without
    // this wait, the very first commands after a connection race the CCCD write and their
    // ACKs are lost (symptom: initial state unpopulated, zeros for things like MCU FW,
    // battery, and notification registrations failing on first try).
    private val cccdDeferredByCharId = mutableMapOf<String, CompletableDeferred<Boolean>>()
    private val cccdMutex = Mutex()

    @OptIn(ExperimentalStdlibApi::class)
    private val blueFalconDelegate = object : BlueFalconDelegate {
        override fun didDiscoverDevice(bluetoothPeripheral: BluetoothPeripheral, advertisementData: Map<AdvertisementDataRetrievalKeys, Any>) {
            val device = BluetoothDevice(
                id = bluetoothPeripheral.uuid,
                name = bluetoothPeripheral.name ?: "Unknown Device",
                rssi = bluetoothPeripheral.rssi?.toInt() ?: -100,
                isConnectable = (advertisementData[AdvertisementDataRetrievalKeys.IsConnectable] as? Int) == 1
            )
            discoveredPeripherals[bluetoothPeripheral.uuid] = bluetoothPeripheral
            if (isSupportedDevice(device)) updateDiscoveredDevices(device)
        }

        override fun didConnect(bluetoothPeripheral: BluetoothPeripheral) {
            _connectedDevice.value = _discoveredDevices.value.find { it.id == bluetoothPeripheral.uuid }
                ?: BluetoothDevice(bluetoothPeripheral.uuid, bluetoothPeripheral.name ?: "Unknown", bluetoothPeripheral.rssi?.toInt() ?: -100, true)
            discoveredPeripherals[bluetoothPeripheral.uuid] = bluetoothPeripheral
            _isReady.value = false
            scope.launch {
                delay(300)
                try { blueFalcon.discoverServices(bluetoothPeripheral) }
                catch (_: Exception) { _isReady.value = false }
            }
        }

        override fun didDisconnect(bluetoothPeripheral: BluetoothPeripheral) {
            if (_connectedDevice.value?.id == bluetoothPeripheral.uuid) {
                _connectedDevice.value = null
                _isReady.value = false
                _discoveredServiceCharacteristics.clear()
            }
        }

        override fun didDiscoverServices(bluetoothPeripheral: BluetoothPeripheral) {
            bluetoothPeripheral.services.values.forEach { service ->
                scope.launch {
                    try { blueFalcon.discoverCharacteristics(bluetoothPeripheral, service) }
                    catch (_: Exception) { }
                }
            }
        }

        override fun didDiscoverCharacteristics(bluetoothPeripheral: BluetoothPeripheral) {
            bluetoothPeripheral.services.values.forEach { service ->
                val charCache = _discoveredServiceCharacteristics.getOrPut(service.uuid.toString().lowercase()) { mutableMapOf() }
                service.characteristics.forEach { charCache[it.uuid.toString().lowercase()] = it }
            }
            val actionsChars = _discoveredServiceCharacteristics[ACTIONS_SERVICE_UUID.lowercase()]
            val hasRequired = actionsChars?.containsKey(ACTIONS_COMMAND_CHARACTERISTIC_UUID.lowercase()) == true &&
                              actionsChars.containsKey(ACTIONS_RESPONSE_CHARACTERISTIC_UUID.lowercase())
            if (hasRequired) _isReady.value = true
        }

        override fun didCharacteristcValueChanged(bluetoothPeripheral: BluetoothPeripheral, bluetoothCharacteristic: BluetoothCharacteristic) {
            bluetoothCharacteristic.value?.let { data ->
                // Keep notification fragments in strict arrival order.
                // OTA packets can be split by BLE stack (e.g. 20 + 3 bytes).
                characteristicEventQueue.trySend(bluetoothCharacteristic.uuid.toString().lowercase() to data)
            }
        }

        override fun didRssiUpdate(bluetoothPeripheral: BluetoothPeripheral) {
            _discoveredDevices.value.find { it.id == bluetoothPeripheral.uuid }?.let { device ->
                updateDiscoveredDevices(device.copy(rssi = bluetoothPeripheral.rssi?.toInt() ?: -100))
            }
        }

        override fun didWriteCharacteristic(bluetoothPeripheral: BluetoothPeripheral, bluetoothCharacteristic: BluetoothCharacteristic, success: Boolean) {
            // iOS: BlueFalcon maps didWriteValueForDescriptor to this callback with the parent
            // characteristic. Android uses onCharacteristicWrite for real GATT characteristic
            // writes only; CCCD completion is delivered via didWriteDescriptor instead.
            val key = bluetoothCharacteristic.uuid.toString().lowercase()
            cccdDeferredByCharId.remove(key)?.complete(success)
        }
        override fun didReadDescriptor(bluetoothPeripheral: BluetoothPeripheral, bluetoothCharacteristicDescriptor: BluetoothCharacteristicDescriptor) {}
        override fun didWriteDescriptor(bluetoothPeripheral: BluetoothPeripheral, bluetoothCharacteristicDescriptor: BluetoothCharacteristicDescriptor) {
            characteristicUuidIfCccdDescriptorWrite(bluetoothCharacteristicDescriptor)
                ?.let { charKey -> cccdDeferredByCharId.remove(charKey)?.complete(true) }
        }
        override fun didUpdateMTU(bluetoothPeripheral: BluetoothPeripheral, status: Int) {
            mtuDeferredByPeripheralId.remove(bluetoothPeripheral.uuid)?.complete(status == 0)
        }
    }

    init {
        if (!blueFalcon.delegates.contains(blueFalconDelegate)) {
            blueFalcon.delegates.add(blueFalconDelegate)
        }
        scope.launch {
            for ((charUuid, data) in characteristicEventQueue) {
                val flow = characteristicMutex.withLock {
                    characteristicUpdates.getOrPut(charUuid) {
                        MutableSharedFlow(extraBufferCapacity = CHARACTERISTIC_FLOW_BUFFER)
                    }
                }
                // Do not use suspending emit here: it would block this loop and freeze delivery
                // of all subsequent BLE events. If the buffer is full, tryEmit drops — raise
                // CHARACTERISTIC_FLOW_BUFFER only if that becomes observable in practice.
                flow.tryEmit(data)
            }
        }
    }

    override fun startScan() {
        if (_isScanning.value) return
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        blueFalcon.scan()
        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (_isScanning.value) stopScan()
        }
    }

    override fun stopScan() {
        if (!_isScanning.value) return
        _isScanning.value = false
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        try { blueFalcon.stopScanning() } catch (_: Exception) {}
    }

    override suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.Default) {
        val peripheral = discoveredPeripherals[device.id] ?: return@withContext false
        if (_connectedDevice.value?.id == peripheral.uuid) return@withContext true
        if (_connectedDevice.value != null) {
            disconnect()
            delay(500)
        }
        return@withContext try {
            blueFalcon.connect(peripheral, autoConnect = false)
            withTimeoutOrNull(5000) {
                _connectedDevice.filterNotNull().first { it.id == peripheral.uuid }
            } != null
        } catch (_: Exception) { false }
    }

    override suspend fun disconnect(): Boolean = withContext(Dispatchers.Default) {
        val device = _connectedDevice.value ?: return@withContext true
        return@withContext try {
            discoveredPeripherals[device.id]?.let { blueFalcon.disconnect(it) }
            withTimeoutOrNull(3000) { _connectedDevice.first { it == null } }
            true
        } catch (_: Exception) {
            _connectedDevice.value = null
            _isReady.value = false
            false
        }
    }

    private suspend fun findCharacteristic(
        peripheral: BluetoothPeripheral,
        serviceUuid: String,
        characteristicUuid: String
    ): BluetoothCharacteristic? = withContext(Dispatchers.Default) {
        val normalizedServiceUuid = serviceUuid.lowercase()
        val normalizedCharUuid = characteristicUuid.lowercase()

        val cachedChar = _discoveredServiceCharacteristics[normalizedServiceUuid]?.get(normalizedCharUuid)
        if (cachedChar != null) return@withContext cachedChar

        val service = peripheral.services.values.find {
            it.uuid.toString().lowercase() == normalizedServiceUuid
        } ?: return@withContext null

        val charCache = _discoveredServiceCharacteristics.getOrPut(service.uuid.toString().lowercase()) {
            mutableMapOf()
        }

        service.characteristics.forEach { char ->
            charCache[char.uuid.toString().lowercase()] = char
        }

        return@withContext service.characteristics.find {
            it.uuid.toString().lowercase() == normalizedCharUuid
        }
    }

    override suspend fun readCharacteristic(serviceUuid: String, characteristicUuid: String): Flow<ByteArray> = flow {
        val device = _connectedDevice.value ?: throw IllegalStateException("Not connected")
        val peripheral = discoveredPeripherals[device.id] ?: throw IllegalStateException("Peripheral not found")
        val characteristic = findCharacteristic(peripheral, serviceUuid, characteristicUuid)
            ?: throw IllegalStateException("Characteristic not found")

        val updateFlow = characteristicMutex.withLock {
            characteristicUpdates.getOrPut(characteristic.uuid.toString().lowercase()) {
                MutableSharedFlow(extraBufferCapacity = CHARACTERISTIC_FLOW_BUFFER)
            }
        }

        try { blueFalcon.readCharacteristic(peripheral, characteristic) }
        catch (_: Exception) { emit(ByteArray(0)); return@flow }

        emit(withTimeoutOrNull(2000) { updateFlow.first() } ?: ByteArray(0))
    }.flowOn(Dispatchers.Default)


    override suspend fun writeCharacteristic(serviceUuid: String, characteristicUuid: String, data: ByteArray): Boolean = gattWriteMutex.withLock {
        withContext(Dispatchers.Default) {
            val device = _connectedDevice.value ?: return@withContext false
            val peripheral = discoveredPeripherals[device.id] ?: return@withContext false
            val characteristic = findCharacteristic(peripheral, serviceUuid, characteristicUuid) ?: return@withContext false
            return@withContext try {
                // BlueFalcon on iOS does not deliver a CBPeripheralDelegate ack for characteristic
                // writes (only for descriptor writes), so we cannot await a write ack here.
                // CoreBluetooth queues and back-pressures the write internally; a successful
                // submit is treated as success. Command-level ACKs are handled at the protocol layer.
                blueFalcon.writeCharacteristicWithoutEncoding(peripheral, characteristic, data, WRITE_TYPE_DEFAULT)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun writeCharacteristicWithoutResponse(serviceUuid: String, characteristicUuid: String, data: ByteArray): Boolean = gattWriteMutex.withLock {
        withContext(Dispatchers.Default) {
            val device = _connectedDevice.value ?: return@withContext false
            val peripheral = discoveredPeripherals[device.id] ?: return@withContext false
            val characteristic = findCharacteristic(peripheral, serviceUuid, characteristicUuid) ?: return@withContext false
            return@withContext try {
                blueFalcon.writeCharacteristicWithoutEncoding(peripheral, characteristic, data, WRITE_TYPE_NO_RESPONSE)
                // iOS typically doesn't deliver didWrite callbacks for Write Without Response.
                // Pace packet emission a little to avoid flooding the stack.
                delay(WRITE_NO_RESPONSE_THROTTLE_MS)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun subscribeToCharacteristic(
        serviceUuid: String,
        characteristicUuid: String
    ): Flow<ByteArray> = withContext(Dispatchers.Default) {
        val device = _connectedDevice.value ?: throw IllegalStateException("Not connected")
        val peripheral = discoveredPeripherals[device.id] ?: throw IllegalStateException("Peripheral not found")
        val characteristic = findCharacteristic(peripheral, serviceUuid, characteristicUuid)
            ?: throw NoSuchElementException("Characteristic not found")

        val charKey = characteristic.uuid.toString().lowercase()

        // Register a CCCD write deferred BEFORE kicking off notifyCharacteristic so we can
        // never miss the delegate callback that completes it. Required on iOS — without this
        // the caller would race subsequent command writes against the CCCD taking effect,
        // losing the ACKs of the first few commands (manifests as zeroed initial state).
        val cccdDeferred = CompletableDeferred<Boolean>()
        cccdMutex.withLock {
            cccdDeferredByCharId[charKey]?.cancel()
            cccdDeferredByCharId[charKey] = cccdDeferred
        }

        val characteristicFlow = characteristicMutex.withLock {
            characteristicUpdates.getOrPut(charKey) {
                MutableSharedFlow(extraBufferCapacity = CHARACTERISTIC_FLOW_BUFFER)
            }
        }

        try {
            blueFalcon.notifyCharacteristic(peripheral, characteristic, true)
        } catch (e: Exception) {
            cccdMutex.withLock { cccdDeferredByCharId.remove(charKey) }
            throw e
        }

        // Wait (bounded) for the CCCD descriptor write to actually ack before returning the
        // flow. If we time out we still proceed — some devices skip the descriptor ack — but
        // on well-behaved peers this guarantees notifications are live before the first
        // command is issued.
        withTimeoutOrNull(2000) { cccdDeferred.await() }
        cccdMutex.withLock {
            if (cccdDeferredByCharId[charKey] === cccdDeferred) cccdDeferredByCharId.remove(charKey)
        }

        flow {
            try {
                characteristicFlow.collect { emit(it) }
            } finally {
                try {
                    if (_connectedDevice.value?.id == peripheral.uuid) {
                        blueFalcon.notifyCharacteristic(peripheral, characteristic, false)
                    }
                } catch (_: Exception) {}
            }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun requestMtu(mtuSize: Int): Boolean = withContext(Dispatchers.Default) {
        val device = _connectedDevice.value ?: return@withContext false
        val peripheral = discoveredPeripherals[device.id] ?: return@withContext false
        val deferred = CompletableDeferred<Boolean>()
        mtuDeferredByPeripheralId[peripheral.uuid] = deferred
        return@withContext try {
            blueFalcon.changeMTU(peripheral, mtuSize)
            withTimeoutOrNull(3000) { deferred.await() } ?: false
        } catch (_: Exception) {
            mtuDeferredByPeripheralId.remove(peripheral.uuid)?.complete(false)
            false
        }
    }

    private fun updateDiscoveredDevices(newDevice: BluetoothDevice) {
        val current = _discoveredDevices.value.toMutableList()
        val index = current.indexOfFirst { it.id == newDevice.id }
        if (index >= 0) current[index] = newDevice else current.add(newDevice)
        _discoveredDevices.value = current.sortedByDescending { it.rssi }
    }

    @Suppress("SimplifyBooleanWithConstants")
    private fun isSupportedDevice(device: BluetoothDevice): Boolean {
        return DEBUG_MODE || findDeviceTypeByName(device.name) != null
    }
} 