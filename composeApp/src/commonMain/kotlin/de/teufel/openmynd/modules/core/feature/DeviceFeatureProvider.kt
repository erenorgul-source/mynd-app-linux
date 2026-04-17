package de.teufel.openmynd.modules.core.feature

import co.touchlab.kermit.Logger
import de.teufel.openmynd.modules.core.bluetooth.connector.DeviceConnector
import de.teufel.openmynd.modules.core.bluetooth.model.BluetoothDevice
import de.teufel.openmynd.modules.core.device.TeufelDeviceType
import de.teufel.openmynd.modules.core.device.findDeviceTypeByName
import de.teufel.openmynd.modules.core.feature.base.FeatureLifecycle
import de.teufel.openmynd.modules.core.protocol.DeviceProtocol
import de.teufel.openmynd.modules.core.protocol.ProtocolFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.reflect.KClass

/**
 * Manages the lifecycle of device protocols and feature states based on the connected device.
 * Acts as a central point for interacting with device features.
 * 
 * Flow:
 * 1. Observes deviceConnector.connectedDevice
 * 2. On device connect: identifies device type → creates protocol → initializes features
 * 3. On device disconnect: clears session → resets all state
 */
class DeviceFeatureProvider : KoinComponent {
    private val logger = Logger.Companion.withTag("DeviceFeatureProvider")
    private val deviceConnector: DeviceConnector by inject()
    private val protocolFactory: ProtocolFactory by inject()
    
    // Scopes:
    // - controllerScope: lives for the entire lifetime of this provider
    // - sessionScope: created per device connection, cancelled on disconnect
    private val controllerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var sessionScope: CoroutineScope? = null
    private var sessionJob: Job? = null

    // UI State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val _connectedDeviceType = MutableStateFlow<TeufelDeviceType?>(null)
    val connectedDeviceType: StateFlow<TeufelDeviceType?> = _connectedDeviceType.asStateFlow()

    // Feature State
    private val _activeFeatures = MutableStateFlow<Map<KClass<out Feature>, Any>>(emptyMap())
    val activeFeatures: StateFlow<Map<KClass<out Feature>, Any>> = _activeFeatures.asStateFlow()

    private val _supportedFeatureTypes = MutableStateFlow<Set<KClass<out Feature>>>(emptySet())
    val supportedFeatureTypes: StateFlow<Set<KClass<out Feature>>> = _supportedFeatureTypes.asStateFlow()

    private val _connectedDeviceFeatures = MutableStateFlow<List<Feature>?>(null)
    val connectedDeviceFeatures: StateFlow<List<Feature>?> = _connectedDeviceFeatures.asStateFlow()

    // Protocol
    private var currentProtocol: DeviceProtocol? = null

    init {
        observeDeviceConnection()
    }

    /**
     * Observes device connection changes and manages the session lifecycle.
     * Creates a new session for each connected device, clears everything on disconnect.
     */
    private fun observeDeviceConnection() = controllerScope.launch {
        deviceConnector.connectedDevice.collect { device ->
            logger.i { "Device connection changed: ${device?.name ?: "null"}" }

            // Clear previous session
            clearSession()

            // If disconnected, we're done
            if (device == null) {
                logger.d { "No device connected, state cleared" }
                return@collect
            }

            // Identify device type
            val deviceType = findDeviceTypeByName(device.name)
            if (deviceType == null) {
                logger.w { "Unsupported device: ${device.name}" }
                _error.value = "Unsupported device: ${device.name}"
                return@collect
            }

            // Start new session
            _connectedDevice.value = device
            _isLoading.value = true
            _error.value = null

            val newSessionScope = CoroutineScope(controllerScope.coroutineContext + SupervisorJob())
            sessionScope = newSessionScope
            sessionJob = newSessionScope.launch session@{
                try {
                    initializeSession(deviceType)
                } catch (e: Exception) {
                    logger.e(e) { "Session initialization failed" }
                    _error.value = "Failed to initialize: ${e.message}"
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Initializes a device session: creates protocol and features.
     */
    private suspend fun initializeSession(deviceType: TeufelDeviceType) {
        logger.i { "Initializing session for ${deviceType.publicName}" }

        // Create protocol based on device type
        val protocol = protocolFactory.create(deviceType, deviceConnector)
        if (protocol == null) {
            logger.w { "No protocol factory mapping for ${deviceType::class.simpleName}" }
            _error.value = "Unsupported protocol for ${deviceType.publicName}"
            _isLoading.value = false
            return
        }
        currentProtocol = protocol

        // Wait for device to be ready
        deviceConnector.isReady.filter { it }.first()
        logger.d { "Device connector ready" }

        // Initialize protocol
        if (!protocol.initialize()) {
            _error.value = "Protocol initialization failed"
            _isLoading.value = false
            return
        }
        logger.d { "Protocol initialized" }

        // Initialize features
        initializeFeatures(deviceType)

        // Session ready
        _connectedDeviceType.value = deviceType
        _isLoading.value = false
        logger.i { "Session initialized successfully" }
    }

    /**
     * Clears the current session: shuts down protocol, cancels jobs, resets state.
     * Must be called from a coroutine context.
     */
    private suspend fun clearSession() {
        logger.d { "Clearing session" }
        
        // Clean up features first
        logger.d { "Cleaning up ${_activeFeatures.value.size} active features" }
        _activeFeatures.value.values.forEach { feature ->
            if (feature is FeatureLifecycle) {
                try {
                    feature.onCleanup()
                    logger.v { "Cleaned up feature: ${feature::class.simpleName}" }
                } catch (e: Exception) {
                    logger.e(e) { "Error cleaning up feature: ${feature::class.simpleName}" }
                }
            }
        }
        
        // Shutdown protocol (while session scope is still active)
        currentProtocol?.let { protocol ->
            try {
                protocol.shutdown()
                logger.d { "Protocol shutdown complete" }
            } catch (e: Exception) {
                logger.e(e) { "Error during protocol shutdown" }
            }
        }
        currentProtocol = null

        // Cancel session scope and jobs
        sessionJob?.cancel()
        sessionScope?.cancel()
        sessionScope = null
        sessionJob = null

        // Reset state
        _activeFeatures.value = emptyMap()
        _supportedFeatureTypes.value = emptySet()
        _connectedDeviceFeatures.value = null
        _connectedDeviceType.value = null
        _connectedDevice.value = null
        _isLoading.value = false
        _error.value = null
        
        logger.d { "Session cleared" }
    }

    private suspend fun initializeFeatures(type: TeufelDeviceType) {
        logger.i { "Initializing features for ${type.publicName}" }
        val protocol = currentProtocol
        if (protocol == null || !protocol.isReady()) {
            logger.w { "Protocol not ready, cannot initialize features" }
            return
        }

        // Get the device's supported features
        val deviceFeatures = type.features
        logger.i { "Device supports ${deviceFeatures.size} features:" }

        deviceFeatures.forEach { logger.i { "- ${it::class.simpleName}" } }

        val supportedFeatureTypes = deviceFeatures.map { it::class }.toSet()
        logger.d { "FEATURE TYPES REGISTERED: ${supportedFeatureTypes.map { it.simpleName }}" }
        _supportedFeatureTypes.value = supportedFeatureTypes

        // Temporary map to build our active features
        val newFeatures = mutableMapOf<KClass<out Feature>, Any>()
        val initializedFeatureDefinitions = mutableListOf<Feature>()

        fun <T : Feature> initFeature(featureClass: KClass<T>) {
            logger.i { "Initializing feature: ${featureClass.simpleName}" }
            try {
                // Find the feature definition from the device type
                val featureDefinition = deviceFeatures.firstOrNull { it::class == featureClass }
                logger.d { "Found feature definition: $featureDefinition" }

                // Ask the protocol to create the feature instance
                if (featureDefinition == null) {
                    logger.w { "No feature definition found for ${featureClass.simpleName}" }
                    return
                }
                val featureInstanceLocal: Any? = protocol.createFeature(featureDefinition)

                if (featureInstanceLocal != null) {
                    logger.i { "Successfully created feature instance: ${featureClass.simpleName}" }
                    newFeatures[featureClass] = featureInstanceLocal
                    initializedFeatureDefinitions.add(featureDefinition)
                    // Publish incrementally so UI can start rendering earlier
                    _activeFeatures.value = newFeatures.toMap()

                    // Avoid redundant immediate fetch here because Base*Feature wrappers
                    // already perform an initial fetch/registration on construction.
                } else {
                    logger.w { "Failed to create feature instance: ${featureClass.simpleName}" }
                }
            } catch (e: IllegalArgumentException) {
                logger.w(e) { "${featureClass.simpleName} feature not supported by this protocol" }
            } catch (e: Exception) {
                logger.e(e) { "Error initializing ${featureClass.simpleName}" }
            }
        }

        // Initialize all supported features in the order provided by the device type
        for (definition in deviceFeatures) {
            val featureClass = definition::class
            initFeature(featureClass)
        }

        // Update active features
        _activeFeatures.value = newFeatures
        _connectedDeviceFeatures.value = initializedFeatureDefinitions
        logger.d { "All active feature types: ${newFeatures.keys.map { it.simpleName }}" }
    }

    /**
     * Called when the ViewModel holding this provider is cleared.
     * Cleans up all resources and cancels all coroutines.
     */
    fun onCleared() {
        logger.d { "onCleared called" }
        // Launch cleanup in controller scope before cancelling it
        controllerScope.launch {
            clearSession()
        }.invokeOnCompletion {
            controllerScope.cancel()
        }
    }

    // Method to check if a feature is supported by the connected device
    fun isFeatureSupported(featureClass: KClass<out Feature>): Flow<Boolean> {
        return supportedFeatureTypes.map { it.contains(featureClass) }
    }
}