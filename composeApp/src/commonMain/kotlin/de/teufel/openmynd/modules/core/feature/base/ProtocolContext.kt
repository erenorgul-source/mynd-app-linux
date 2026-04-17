package de.teufel.openmynd.modules.core.feature.base

import de.teufel.openmynd.modules.core.protocol.AckPayload
import de.teufel.openmynd.modules.core.protocol.NotificationPayload
import kotlinx.coroutines.flow.Flow
import kotlin.UShort

/**
 * Provides the communication primitives needed by base features.
 * Protocols should implement this interface to allow features to interact with the device.
 */
interface ProtocolContext {
    /** Send a command to the device. */
    suspend fun sendCommand(cmdId: UShort, payload: ByteArray): Boolean
    
    /** Flow of ACK responses from the device. */
    val ackPayloads: Flow<AckPayload>
    
    /** Flow of notification messages from the device. */
    val notificationPayloads: Flow<NotificationPayload>
    
    /** Register for a specific notification type (protocol-specific implementation). */
    suspend fun registerForNotification(notification: TeufelNotification): Boolean = true
}

