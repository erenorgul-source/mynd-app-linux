package de.teufel.openmynd.modules.screens.knownDevices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.teufel.openmynd.modules.core.bluetooth.connector.DeviceConnector
import de.teufel.openmynd.modules.core.bluetooth.repository.KnownDevicesRepository
import de.teufel.openmynd.modules.core.bluetooth.repository.StoredDevice
import de.teufel.openmynd.modules.screens.searchDevices.ConnectionState
import de.teufel.openmynd.modules.ui.IUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import co.touchlab.kermit.Logger
import de.teufel.openmynd.modules.core.device.findDeviceTypeByName

class KnownDevicesViewModel: ViewModel(), KoinComponent {
    private val logger = Logger.withTag("KnownDevicesVM")
    private val deviceConnector: DeviceConnector by inject()
    private val knownDevicesRepository: KnownDevicesRepository by inject()
    
    private val _isRefreshing = MutableStateFlow(false)
    private val _connectionMessage = MutableStateFlow<String?>(null)
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    private var scanJob: Job? = null
    
    // Track if we've ever had a device connected during this session
    private var hadPreviousConnection = false
    
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    val state: StateFlow<UiState> = knownDevicesRepository.getKnownDevicesFlow()
        .map { devices ->
            if (devices.isEmpty()) {
                UiState.NoData(isRefreshing = _isRefreshing.value)
            } else {
                UiState.HasData(
                    isRefreshing = _isRefreshing.value,
                    devices = devices,
                    message = _connectionMessage.value
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading(isRefreshing = true)
        )
        
    init {
        loadKnownDevices()
        
        // Listen for new connected devices
        viewModelScope.launch {
            deviceConnector.connectedDevice.collectLatest { device ->
                device?.let {
                    knownDevicesRepository.addKnownDevice(it)
                    val publicName = findDeviceTypeByName(it.name)?.publicName ?: it.name
                    _connectionMessage.value = "Connected to ${publicName}"
                    _connectionState.value = ConnectionState.Connected(it)
                    hadPreviousConnection = true
                } ?: run {
                    // Only show disconnection message if we had a previous connection
                    if (hadPreviousConnection) {
                        _connectionMessage.value = "Device disconnected"
                        _connectionState.value = ConnectionState.Disconnected
                    } else {
                        // Initial state - just set the state without a message
                        _connectionState.value = ConnectionState.Idle
                    }
                }
            }
        }

        // Monitor scanning state
        viewModelScope.launch {
            deviceConnector.isScanning.collectLatest { scanning ->
                if (!scanning && _connectionState.value is ConnectionState.Connecting) {
                    // If scanning stopped but we're still in connecting state, it's likely a timeout
                    if (_connectionMessage.value?.contains("Found") != true) {
                        _connectionMessage.value = "Scan timed out. Please try again or make sure the device is powered on."
                    }
                }
            }
        }
    }
    
    private fun loadKnownDevices() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                knownDevicesRepository.getKnownDevices()
                _isRefreshing.value = false
            } catch (_: Exception) {
                _isRefreshing.value = false
            }
        }
    }
    
    fun removeDevice(deviceId: String) {
        viewModelScope.launch {
            knownDevicesRepository.removeKnownDevice(deviceId)
        }
    }
    
    fun clearAllDevices() {
        viewModelScope.launch {
            knownDevicesRepository.clearAllKnownDevices()
        }
    }
    
    fun clearMessage() {
        _connectionMessage.value = null
    }
    
    fun connectToDevice(device: StoredDevice) {
        // Cancel any existing scan job
        scanJob?.cancel()
        
        scanJob = viewModelScope.launch {
            _isRefreshing.value = true
            _connectionMessage.value = "Scanning for ${device.name}..."
            _connectionState.value = ConnectionState.Connecting(device.id)
            
            try {
                // Start scanning for devices
                logger.i { "Starting scan to find device with ID: ${device.id}" }
                deviceConnector.startScan()
                
                // Use a shorter timeout (7 seconds)
                val timeoutMillis = 7000L
                
                // Use the discoveredDevices flow to find the matching device
                val discoveredDevice = withTimeoutOrNull(timeoutMillis) {
                    deviceConnector.discoveredDevices
                        .filter { devices -> 
                            devices.any { it.id == device.id }
                        }
                        .map { devices ->
                            devices.first { it.id == device.id }
                        }
                        .first()
                }
                
                if (discoveredDevice != null) {
                    val publicName = findDeviceTypeByName(discoveredDevice.name)?.publicName ?: discoveredDevice.name
                    _connectionMessage.value = "Found ${publicName}, connecting..."
                    logger.i { "Found matching device in scan: ${discoveredDevice.name}" }
                    
                    // Update user on connection attempt
                    val success = deviceConnector.connect(discoveredDevice)
                    if (!success) {
                        _connectionMessage.value = "Failed to connect to ${publicName}. Please try again."
                        _connectionState.value = ConnectionState.Failed("Connection failed")
                    } else {
                        _connectionMessage.value = "Connecting to ${publicName}..."
                        // Connection success/failure will be handled by the connectedDevice flow
                    }
                } else {
                    _connectionMessage.value = "${device.name} not found after ${timeoutMillis/1000} seconds. Is it powered on and in range?"
                    _connectionState.value = ConnectionState.Failed("Device not found")
                }
                
                // Ensure scan is stopped
                deviceConnector.stopScan()
                _isRefreshing.value = false
                
            } catch (e: Exception) {
                logger.e(e) { "Exception in connectToDevice" }
                _isRefreshing.value = false
                _connectionMessage.value = "Error connecting: ${e.message}"
                _connectionState.value = ConnectionState.Failed(e.message)
                
                // Make sure to stop scanning in case of error
                try {
                    deviceConnector.stopScan()
                } catch (_: Exception) {
                    // Ignore any errors when trying to stop scan
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        
        // Make sure to stop scanning when ViewModel is cleared
        viewModelScope.launch {
            try {
                deviceConnector.stopScan()
            } catch (_: Exception) {
                // Ignore any errors when trying to stop scan
            }
        }
    }
}

// TODO: Move into separate file
sealed interface UiState : IUiState {

    data class Loading(
        override val isRefreshing: Boolean = false,
        override val isError: Boolean = false,
        override val isEmpty: Boolean = false,
        override val errorMessage: String? = null,
    ) : UiState {
        override val isLoadingMore = true
    }

    data class NoData(
        override val isRefreshing: Boolean = false,
        override val isError: Boolean = false,
        override val isEmpty: Boolean = true,
        override val errorMessage: String? = null
    ) : UiState {
        override val isLoadingMore = false
    }

    data class HasData(
        override val isRefreshing: Boolean,
        val devices: List<StoredDevice>,
        val message: String? = null,
        override val isError: Boolean = false,
        override val isEmpty: Boolean = false
    ) : UiState {
        override val isLoadingMore = false
        override val errorMessage = message
    }
}