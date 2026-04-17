package de.teufel.openmynd.modules.screens.searchDevices

import org.koin.core.component.KoinComponent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.teufel.openmynd.modules.core.bluetooth.connector.DeviceConnector
import de.teufel.openmynd.modules.ui.IUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import de.teufel.openmynd.modules.core.bluetooth.model.BluetoothDevice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import de.teufel.openmynd.modules.core.bluetooth.repository.KnownDevicesRepository
import co.touchlab.kermit.Logger
import de.teufel.openmynd.modules.core.device.findDeviceTypeByName
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

sealed class ConnectionState { 
    object Idle : ConnectionState()
    // Add address/peripheral to Connecting/Failed for context
    data class Connecting(val address: String?) : ConnectionState()
    data class Connected(val device: BluetoothDevice) : ConnectionState()
    data class Failed(val error: String?) : ConnectionState()
    object Disconnected : ConnectionState()
}

data class SearchDeviceUiState(
    val isLoading: Boolean = false,
    val devices: List<BluetoothDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Idle,
    val error: String? = null
) : IUiState {
    override val isRefreshing: Boolean get() = isLoading
    override val isError: Boolean get() = error != null
    override val isEmpty: Boolean get() = devices.isEmpty() && !isLoading
    override val isLoadingMore: Boolean = false // Not used here
    override val errorMessage: String? get() = error
}

class SearchDevicesViewModel : ViewModel(), KoinComponent {
    private val logger = Logger.withTag("SearchDevicesVM")
    private val deviceConnector: DeviceConnector by inject()
    private val knownDevicesRepository: KnownDevicesRepository by inject()

    private val _isConnecting = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null) // General errors

    // UI state - Combine flows from connector and local state
    val uiState: StateFlow<SearchDeviceUiState> = combine(
        deviceConnector.isScanning, 
        deviceConnector.discoveredDevices, 
        deviceConnector.connectedDevice, 
        _isConnecting,
        _error
    ) { isScanning, discovered, connected, connectingId, error ->
        // Map device names to public names when possible
        val mapped = discovered.map { dev ->
            val public = findDeviceTypeByName(dev.name)?.publicName
            if (public != null && public != dev.name) dev.copy(name = public) else dev
        }
        SearchDeviceUiState(
            isLoading = isScanning || connectingId != null,
            devices = mapped,
            connectionState = when {
                connected != null -> ConnectionState.Connected(connected) // Pass the whole device object
                connectingId != null -> ConnectionState.Connecting(connectingId)
                error != null -> ConnectionState.Failed(error) // Show error if not connecting
                else -> ConnectionState.Idle
            },
            error = error // Show general error
        )
    }.stateIn( // Add stateIn operator
        scope = viewModelScope, 
        started = SharingStarted.WhileSubscribed(5000), 
        initialValue = SearchDeviceUiState() // Provide initial value
    )

    private var lastScanTimeMs: Long = 0
    private val minScanIntervalMs = 3000
    private var initialScanPerformed: Boolean = false

    /**
     * Start BLE scan
     */
    @OptIn(ExperimentalTime::class)
    fun startScan() {
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastScanTimeMs < minScanIntervalMs && uiState.value.devices.isNotEmpty()) {
            logger.v { "Scan requested too soon, skipping." }
            return
        }
        if (uiState.value.isLoading) {
            logger.v { "Scan already in progress, skipping." }
            return
        }
        
        lastScanTimeMs = now
        viewModelScope.launch {
            _error.value = null // Clear previous errors
            try {
                // Trigger scan; uiState listens to discoveredDevices
                deviceConnector.startScan()
            } catch (e: Exception) {
                logger.e(e) { "Error starting scan via connector" }
                _error.value = "Error starting scan: ${e.message}"
            }
        }
    }

    /**
     * Performs the scan only if it hasn't been done automatically on first launch.
     */
    fun performInitialScan() {
        if (!initialScanPerformed) {
            logger.d { "Performing initial scan..." }
            initialScanPerformed = true
            // Reset scan time to allow immediate initial scan
            lastScanTimeMs = 0 
            startScan()
        } else {
            logger.v { "Initial scan already performed, skipping." }
        }
    }

    fun stopScan() = deviceConnector.stopScan()

    /**
     * Connect to a selected device
     */
    fun connectToDevice(device: BluetoothDevice) {
        if (uiState.value.connectionState != ConnectionState.Idle) {
            logger.v { "Connection attempt while not idle, ignoring." }
            return
        }
        
        viewModelScope.launch {
            _isConnecting.value = device.id
            _error.value = null
            try {
                val success = deviceConnector.connect(device)
                if (success) {
                    logger.i { "Connection initiated successfully for ${device.id}" }
                    // Add the device to known devices
                    knownDevicesRepository.addKnownDevice(device)
                    // Connection status will update via connectedDevice flow
                } else {
                    logger.w { "Failed to initiate connection for ${device.id}" }
                    _error.value = "Failed to connect to ${device.name}"
                    _isConnecting.value = null
                }
            } catch (e: Exception) {
                logger.e(e) { "Exception during connection attempt" }
                _error.value = "Connection error: ${e.message}"
                _isConnecting.value = null
            }
        }
    }
    
    /**
     * Disconnect from the currently connected device
     */
    fun disconnectDevice() {
        viewModelScope.launch {
            _error.value = null
            try {
                val success = deviceConnector.disconnect()
             if (!success) {
                    _error.value = "Failed to disconnect."
                }
                // Status will update via connectedDevice flow
            } catch (e: Exception) {
                _error.value = "Error disconnecting: ${e.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        deviceConnector.stopScan() // Ensure scan is stopped
    }
}