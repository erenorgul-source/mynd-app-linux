package de.teufel.openmynd.modules.screens.controlDevices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.teufel.openmynd.modules.core.bluetooth.connector.DeviceConnector
import de.teufel.openmynd.modules.core.feature.DeviceFeatureProvider
import de.teufel.openmynd.modules.core.feature.*
import de.teufel.openmynd.modules.core.feature.base.ReadFeature
import de.teufel.openmynd.modules.ui.IUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.reflect.KClass
import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay

/**
 * Simplified UI State for the control devices screen, 
 * containing only the base information about the device connection
 */
data class ControlDeviceUiState(
    val isLoading: Boolean = false,
    val deviceName: String? = null,
    val error: String? = null,
    val lastMessage: String? = null
) : IUiState {
    override val isEmpty: Boolean get() = deviceName == null && !isLoading
    override val isError: Boolean get() = error != null
    override val isRefreshing: Boolean get() = isLoading
    override val errorMessage: String? get() = error
    override val isLoadingMore: Boolean = false
}

class ControlDevicesViewModel : ViewModel(), KoinComponent {
    private val logger = Logger.withTag("ControlDevicesVM")

    // Inject the DeviceFeatureProvider
    private val featureProvider: DeviceFeatureProvider by inject()
    // Inject DeviceConnector needed for disconnect
    private val deviceConnector: DeviceConnector by inject()

    // Local state for transient UI messages
    private val _lastMessage = MutableStateFlow<String?>(null)
    // Track which features have been explicitly fetched to avoid duplicate fetches as list items
    // get disposed/recreated during scroll.
    private val requestedFetches = MutableStateFlow<Set<KClass<out Feature>>>(emptySet())

    // Basic UI state with connection info only
    val uiState: StateFlow<ControlDeviceUiState> = combine(
        featureProvider.isLoading,
        featureProvider.error,
        featureProvider.connectedDeviceType,
        _lastMessage
    ) { isLoading, error, deviceType, lastMessage ->
        ControlDeviceUiState(
            isLoading = isLoading,
            deviceName = deviceType?.publicName,
            error = error,
            lastMessage = lastMessage
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ControlDeviceUiState(isLoading = true)
    )

    // Flow for signalling navigation events
    private val _navigationEvent = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 1)
    val navigationEvent = _navigationEvent.asSharedFlow()

    init {
        logger.i { "Initialized. Provider will handle connection." }
        // Navigate back when device disconnects
        viewModelScope.launch {
            featureProvider.connectedDevice.collect { device ->
                if (device == null) {
                    _navigationEvent.tryEmit(NavigationEvent.NavigateBack)
                }
            }
        }

        // Reset per-device fetch guards when device type changes (new connection)
        viewModelScope.launch {
            featureProvider.connectedDeviceType.collect {
                requestedFetches.value = emptySet()
            }
        }
    }

    fun disconnectDevice() {
        viewModelScope.launch {
            _lastMessage.value = "Disconnecting…"
            try {
                deviceConnector.disconnect() // fire‑and‑forget
            } finally {
                _navigationEvent.tryEmit(NavigationEvent.NavigateBack)
            }
        }
    }

    // Removed override to support KMP common ViewModel without AndroidX 'onCleared' availability
    fun clear() {
        logger.d { "clear()" }
        featureProvider.onCleared()
    }

    // NavigationEvent sealed class remains the same
    sealed class NavigationEvent {
        data object NavigateBack : NavigationEvent()
    }

    /**
     * Method to check if a feature is supported by the connected device
     */
    fun isFeatureSupported(featureClass: KClass<out Feature>): Flow<Boolean> =
        featureProvider.isFeatureSupported(featureClass)

    /**
     * Gets a specific feature implementation if available
     */
    @Composable
    fun <T> getFeature(featureClass: KClass<out Feature>): T? {
        val featureRef by remember(featureClass) {
            featureProvider.activeFeatures
                .map { it[featureClass] }
                .distinctUntilChanged()
        }.collectAsState(initial = null)
        @Suppress("UNCHECKED_CAST")
        return featureRef as? T
    }

    /**
     * Utility function to show a message to the user
     */
    fun showMessage(message: String) {
        _lastMessage.value = message
    }

    /**
     * Refreshes the state of a feature by calling its fetch method
     * 
     * @param featureClass The class of the feature to refresh
     * @return True if the refresh was requested, false otherwise
     */
    fun refreshFeature(featureClass: KClass<out Feature>): Boolean {
        // Avoid duplicate fetches for the same feature while connected to the same device
        if (requestedFetches.value.contains(featureClass)) return true
        // Access the current value from featureProvider
        val activeFeatures = featureProvider.activeFeatures.value
        val featureInstance = activeFeatures[featureClass] ?: return false
        
        viewModelScope.launch {
            try {
                logger.d { "Refreshing ${featureClass.simpleName}" }

                val success = (featureInstance as? ReadFeature<*>)?.fetch() ?: false

                if (success) logger.v { "Refresh ${featureClass.simpleName} result: $success" }
                // Only mark complete on success so a failed/stale prefetch does not block retries
                if (success) requestedFetches.value += featureClass
            } catch (e: Exception) {
                logger.w(e) { "Error refreshing ${featureClass.simpleName}" }
            }
        }
        
        return true
    }

    /**
     * Prefetch all currently active features once after connection to seed UI state,
     * minimizing on-demand loads when items enter the viewport.
     */
    fun prefetchSupportedFeatures() {
        viewModelScope.launch {
            // Debounce slightly to avoid colliding with features' own initial fetch
            delay(200)
            val active = featureProvider.activeFeatures.value
            active.forEach { (kClass, featureInstance) ->
                if (!requestedFetches.value.contains(kClass)) {
                    try {
                        logger.d { "Prefetching ${kClass.simpleName}" }
                        val success = (featureInstance as? ReadFeature<*>)?.fetch() ?: false
                        if (success) logger.v { "Prefetch ${kClass.simpleName} result: $success" }
                        if (success) requestedFetches.value += kClass
                    } catch (e: Exception) {
                        logger.w(e) { "Prefetch failed for ${kClass.simpleName}" }
                    }
                }
            }
        }
    }
}