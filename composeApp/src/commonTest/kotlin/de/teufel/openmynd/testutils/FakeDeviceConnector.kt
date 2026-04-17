package de.teufel.openmynd.testutils

import de.teufel.openmynd.modules.core.bluetooth.connector.DeviceConnector
import de.teufel.openmynd.modules.core.bluetooth.model.BluetoothDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

class FakeDeviceConnector : DeviceConnector {
    override val isScanning: StateFlow<Boolean> = MutableStateFlow(false)
    override val discoveredDevices: StateFlow<List<BluetoothDevice>> = MutableStateFlow(emptyList())
    override val connectedDevice: StateFlow<BluetoothDevice?> = MutableStateFlow(null)
    override val isReady: StateFlow<Boolean> = MutableStateFlow(false)

    override fun startScan() = Unit

    override fun stopScan() = Unit

    override suspend fun connect(device: BluetoothDevice): Boolean = true

    override suspend fun disconnect(): Boolean = true

    override suspend fun readCharacteristic(serviceUuid: String, characteristicUuid: String): Flow<ByteArray> =
        emptyFlow()

    override suspend fun writeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray,
    ): Boolean = true

    override suspend fun writeCharacteristicWithoutResponse(
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray
    ): Boolean = true

    override suspend fun subscribeToCharacteristic(serviceUuid: String, characteristicUuid: String): Flow<ByteArray> =
        emptyFlow()

    override suspend fun requestMtu(mtuSize: Int): Boolean = true
}
