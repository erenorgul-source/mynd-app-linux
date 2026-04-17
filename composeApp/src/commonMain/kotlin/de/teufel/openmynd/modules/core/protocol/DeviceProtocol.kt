package de.teufel.openmynd.modules.core.protocol

import de.teufel.openmynd.modules.core.feature.Feature
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Base interface for device-specific protocols.
 * Protocols implement specific communication patterns for different device types.
 */
interface DeviceProtocol {

    /**
     * Initialize communication with the device.
     * This should be called after connecting to set up any initial state.
     * @return true if initialization was successful
     */
    suspend fun initialize(): Boolean
    
    /**
     * Performs a graceful shutdown of the protocol.
     * This should be called before disconnecting to clean up any resources.
     */
    suspend fun shutdown()
    
    /**
     * Checks if the protocol is ready to send commands.
     * @return true if the protocol is ready
     */
    fun isReady(): Boolean

    /**
     * Flow emitting parsed ACK payloads: command ID to its payload bytes.
     */
    val ackPayloads: Flow<AckPayload>

    /**
     * Flow emitting parsed Notification payloads: command ID to its payload bytes.
     */
    val notificationPayloads: Flow<NotificationPayload>

    /**
     * Return feature instance for a given feature definition, or null if unsupported by this protocol.
     * The returned instance should implement the contract interface matching the feature type.
     */
    fun createFeature(definition: Feature): Any?

    /** Set of supported feature types for quick checks. */
    fun supportedFeatureTypes(): Set<KClass<out Feature>>
}