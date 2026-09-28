package de.teufel.openmynd.modules.core.bluetooth.connector

import co.touchlab.kermit.Logger
import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.characteristicOf
import com.juul.kable.peripheral
import de.teufel.openmynd.modules.core.bluetooth.model.BluetoothDevice
import de.teufel.openmynd.modules.core.device.findDeviceTypeByName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class KableDeviceConnector : DeviceConnector {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val logger = Logger.withTag("KableConnector")
    private val DEBUG_MODE = false
    private val SCAN_TIMEOUT_MS = 7000L

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<BluetoothDevice>> =
        _discoveredDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    override val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val advertisementById = mutableMapOf<String, Advertisement>()
    private val peripheralById = mutableMapOf<String, Peripheral>()
    private var scanJob: Job? = null
    private var scanTimeoutJob: Job? = null

    override fun startScan() {
        if (_isScanning.value) return
        logger.i { "Starting Kable scan…" }
        _isScanning.value = true
        _discoveredDevices.value = emptyList()
        scanJob?.cancel()
        scanTimeoutJob?.cancel()
        scanJob = scope.launch {
            Scanner().advertisements
                .catch { e ->
                    logger.e(e) { "Kable scan error" }
                    _isScanning.value = false
                }
                .collect { adv ->
                    val id = adv.identifier.toString()
                    val rssi = adv.rssi ?: -100
                    val name = adv.name ?: "Unknown Device"
                    val device = BluetoothDevice(
                        id = id,
                        name = name,
                        rssi = rssi,
                        isConnectable = true
                    )
                    if (DEBUG_MODE || isSupportedDevice(device)) {
                        advertisementById[id] = adv
                        updateDiscoveredDevices(device)
                        logger.v { "Discovered supported device: ${device.name} (${device.id}) rssi=$rssi" }
                    } else {
                        logger.v { "Discovered unsupported device: ${device.name} (${device.id}) rssi=$rssi" }
                    }
                }
        }
        scanTimeoutJob = scope.launch {
            try {
                kotlinx.coroutines.delay(SCAN_TIMEOUT_MS)
                if (_isScanning.value) {
                    logger.i { "Auto-stopping scan after ${SCAN_TIMEOUT_MS}ms" }
                    stopScan()
                }
            } catch (_: Throwable) {
            }
        }
    }

    override fun stopScan() {
        logger.i { "Stopping Kable scan" }
        _isScanning.value = false
        scanJob?.cancel()
        scanJob = null
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
    }

    override suspend fun connect(device: BluetoothDevice): Boolean {
        val id = device.id
        if (_connectedDevice.value?.id == id) return true
        peripheralById[id]?.let { existing ->
            if (_connectedDevice.value != null && _connectedDevice.value?.id != id) {
                disconnect()
            }
        }
        val adv = advertisementById[id] ?: return false
        val peripheral = scope.peripheral(adv)
        peripheralById[id] = peripheral
        return try {
            val connected = kotlinx.coroutines.withTimeoutOrNull(8000) {
                peripheral.connect()
                true
            } ?: false
            if (!connected) {
                logger.w { "Kable connect timeout for ${device.id}" }
                return false
            }
            _connectedDevice.value = device
            _isReady.value = true
            true
        } catch (t: Throwable) {
            logger.e(t) { "Error connecting via Kable" }
            _connectedDevice.value = null
            _isReady.value = false
            false
        }
    }

    override suspend fun disconnect(): Boolean {
        val device = _connectedDevice.value ?: return true
        return try {
            peripheralById[device.id]?.disconnect()
            _connectedDevice.value = null
            _isReady.value = false
            true
        } catch (_: Throwable) {
            _connectedDevice.value = null
            _isReady.value = false
            false
        }
    }

    override suspend fun readCharacteristic(
        serviceUuid: String,
        characteristicUuid: String
    ): Flow<ByteArray> = flow {
        val device = _connectedDevice.value ?: throw IllegalStateException("Not connected")
        val peripheral =
            peripheralById[device.id] ?: throw IllegalStateException("Peripheral not found")
        val characteristic =
            characteristicOf(service = serviceUuid, characteristic = characteristicUuid)
        val data = peripheral.read(characteristic)
        emit(data)
    }

    override suspend fun writeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray
    ): Boolean {
        val device = _connectedDevice.value ?: return false
        val peripheral = peripheralById[device.id] ?: return false
        val characteristic =
            characteristicOf(service = serviceUuid, characteristic = characteristicUuid)
        return try {
            peripheral.write(characteristic, data)
            true
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun writeCharacteristicWithoutResponse(
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray
    ): Boolean {
        val device = _connectedDevice.value ?: return false
        val peripheral = peripheralById[device.id] ?: return false
        val characteristic =
            characteristicOf(service = serviceUuid, characteristic = characteristicUuid)
        return try {
            peripheral.write(characteristic, data, com.juul.kable.WriteType.WithoutResponse)
            true
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun subscribeToCharacteristic(
        serviceUuid: String,
        characteristicUuid: String
    ): Flow<ByteArray> {
        // Kable's peripheral.observe already takes care of the CCCD write internally and
        // suspends as needed on Android, so nothing extra to do here.
        val device = _connectedDevice.value ?: error("Not connected")
        val peripheral = peripheralById[device.id] ?: error("Peripheral not found")
        val c = characteristicOf(service = serviceUuid, characteristic = characteristicUuid)
        return peripheral.observe(c)
    }

    override suspend fun requestMtu(mtuSize: Int): Boolean {
        // No-op for now; Kable requestMtu not available on non-Android targets in this project setup
        return false
    }

    fun cleanup() {
        try {
            stopScan()
        } catch (_: Throwable) {
        }
        scope.cancel()
    }

    private fun isSupportedDevice(device: BluetoothDevice): Boolean {
        return findDeviceTypeByName(device.name) != null
    }

    private fun updateDiscoveredDevices(newDevice: BluetoothDevice) {
        val current = _discoveredDevices.value.toMutableList()
        val index = current.indexOfFirst { it.id == newDevice.id }
        if (index >= 0) current[index] = newDevice else current.add(newDevice)
        _discoveredDevices.value = current.sortedByDescending { it.rssi }
    }

}
