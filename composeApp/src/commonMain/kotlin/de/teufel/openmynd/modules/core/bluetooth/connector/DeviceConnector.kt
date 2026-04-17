package de.teufel.openmynd.modules.core.bluetooth.connector

import de.teufel.openmynd.modules.core.bluetooth.model.BluetoothDevice
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow

/**
 * Central interface for managing Bluetooth device connections and communication.
 * Provides a consistent API for scanning, connecting, and interacting with peripherals.
 */
interface DeviceConnector {
    /**
     * Current scanning state
     */
    val isScanning: StateFlow<Boolean>
    
    /**
     * Flow of discovered BLE devices
     */
    val discoveredDevices: StateFlow<List<BluetoothDevice>>
    
    /**
     * Currently connected device, or null if not connected
     */
    val connectedDevice: StateFlow<BluetoothDevice?>
    
    /**
     * Flow indicating if the connected device is fully discovered and ready for communication.
     * Emits true when services and necessary characteristics are found, false otherwise.
     */
    val isReady: StateFlow<Boolean>
    
    /**
     * Starts scanning for BLE devices.
     * Discovered devices are available via the [discoveredDevices] StateFlow.
     */
    fun startScan()
    
    /**
     * Stop the current scan.
     */
    fun stopScan()
    
    /**
     * Connect to a specific device.
     * @param device The device to connect to
     * @return true if connection initiated successfully
     */
    suspend fun connect(device: BluetoothDevice): Boolean

    /**
     * Disconnect from the current device.
     * @return true if disconnection was initiated successfully
     */
    suspend fun disconnect(): Boolean
    
    /**
     * Read a characteristic value from the connected device.
     * @param serviceUuid UUID of the service containing the characteristic
     * @param characteristicUuid UUID of the characteristic to read
     * @return Flow emitting read values
     */
    suspend fun readCharacteristic(serviceUuid: String, characteristicUuid: String): Flow<ByteArray>
    
    /**
     * Write a value to a characteristic.
     * @param serviceUuid UUID of the service containing the characteristic
     * @param characteristicUuid UUID of the characteristic to write to
     * @param data Data to write
     * @return true if write was successful
     */
    suspend fun writeCharacteristic(serviceUuid: String, characteristicUuid: String, data: ByteArray): Boolean

    /**
     * Write a value to a characteristic using write-without-response mode.
     * Preferred for high-throughput data transfer (e.g., OTA firmware upgrades).
     * @param serviceUuid UUID of the service containing the characteristic
     * @param characteristicUuid UUID of the characteristic to write to
     * @param data Data to write
     * @return true if write was initiated successfully
     */
    suspend fun writeCharacteristicWithoutResponse(serviceUuid: String, characteristicUuid: String, data: ByteArray): Boolean
    
    /**
     * Subscribe to notifications from a characteristic. Suspends until the subscription
     * (the underlying CCCD descriptor write) has actually taken effect on the peer, so the
     * caller can safely issue commands that expect ACK notifications right after this
     * returns. Without this guarantee, early commands race the CCCD write and their ACKs
     * are silently lost on iOS (they arrive before the flow is collecting).
     *
     * @param serviceUuid UUID of the service containing the characteristic
     * @param characteristicUuid UUID of the characteristic to subscribe to
     * @return Flow emitting notification values
     */
    suspend fun subscribeToCharacteristic(serviceUuid: String, characteristicUuid: String): Flow<ByteArray>
    
    /**
     * Request the MTU (Maximum Transmission Unit) size change.
     * @param mtuSize Requested MTU size
     * @return true if request was initiated successfully
     */
    suspend fun requestMtu(mtuSize: Int): Boolean
} 