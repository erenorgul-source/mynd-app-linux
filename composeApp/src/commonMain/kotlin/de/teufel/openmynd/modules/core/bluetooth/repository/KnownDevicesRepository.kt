package de.teufel.openmynd.modules.core.bluetooth.repository

import com.russhwolf.settings.ExperimentalSettingsApi
import de.teufel.openmynd.modules.core.bluetooth.model.BluetoothDevice
import de.teufel.openmynd.modules.core.device.findDeviceTypeByName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.encodeValue
import com.russhwolf.settings.serialization.decodeValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class StoredDevice(
    val id: String,
    val name: String,
    val lastConnectedTimestamp: Long
)

private const val KEY_KNOWN_DEVICES = "known_devices_json"

/**
 * Repository for managing known/previously connected Bluetooth devices
 */
expect fun provideSettings(): Settings

@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
class KnownDevicesRepository : KoinComponent {
    private val settings: Settings = provideSettings()
    private val _devices = MutableStateFlow<List<StoredDevice>>(emptyList())
    private val devices: StateFlow<List<StoredDevice>> = _devices.asStateFlow()

    init {
        // Load once at startup
        runCatching {
            val loaded: List<StoredDevice> = settings.decodeValue(
                ListSerializer(StoredDevice.serializer()),
                KEY_KNOWN_DEVICES,
                emptyList()
            )
            _devices.value = loaded
        }.onFailure { _devices.value = emptyList() }
    }
    
    /**
     * Get list of known devices
     */
    fun getKnownDevices(): List<StoredDevice> = devices.value
    
    /**
     * Get flow of known devices
     */
    fun getKnownDevicesFlow(): Flow<List<StoredDevice>> = devices
    
    /**
     * Add a device to known devices list
     */
    @OptIn(ExperimentalTime::class)
    fun addKnownDevice(device: BluetoothDevice) {
        val currentDevices = devices.value
        val storedDevice = StoredDevice(
            id = device.id,
            name = findDeviceTypeByName(device.name)?.publicName ?: device.name,
            lastConnectedTimestamp = Clock.System.now().toEpochMilliseconds()
        )
        
        // If device already exists, update timestamp
        val newList = if (currentDevices.any { it.id == device.id }) {
            currentDevices.map { 
                if (it.id == device.id) storedDevice else it 
            }
        } else {
            currentDevices + storedDevice
        }
        
        persist(newList)
    }
    
    /**
     * Remove a device from known devices list
     */
    fun removeKnownDevice(deviceId: String) {
        val currentDevices = devices.value
        val newList = currentDevices.filter { it.id != deviceId }
        persist(newList)
    }
    
    /**
     * Clear all known devices
     */
    fun clearAllKnownDevices() {
        persist(emptyList())
    }

    @OptIn(ExperimentalSettingsApi::class)
    private fun persist(list: List<StoredDevice>) {
        _devices.value = list
        settings.encodeValue(ListSerializer(StoredDevice.serializer()), KEY_KNOWN_DEVICES, list)
    }
} 