package de.teufel.openmynd.modules.core.feature.sourceselection.actions

import de.teufel.openmynd.modules.core.feature.SourceSelection
import de.teufel.openmynd.modules.core.feature.base.FeatureLifecycle
import de.teufel.openmynd.modules.core.feature.sourceselection.SourceSelectionFeature
import de.teufel.openmynd.modules.core.feature.sourceselection.SourceType
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import de.teufel.openmynd.modules.core.protocol.actions.ActionsNotification
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Implementation of SourceSelectionFeature for the MYND speaker using Actions protocol.
 * NOTE: This feature requires custom polling, non-blocking sends, and multiple value tracking,
 * so it uses a custom implementation rather than the simplified base classes.
 */
class ActionsSourceSelectionFeature(
    private val protocol: ActionsProtocol,
    @Suppress("UNUSED_PARAMETER") definition: SourceSelection = SourceSelection()
) : SourceSelectionFeature, FeatureLifecycle {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _currentSource = MutableStateFlow<SourceType?>(null)
    override val currentSource: StateFlow<SourceType?> = _currentSource.asStateFlow()
    
    private val _connectedSources = MutableStateFlow<Set<SourceType>?>(null)
    override val connectedSources: StateFlow<Set<SourceType>?> = _connectedSources.asStateFlow()

    init {
        // Listen to ACKs for current source
        protocol.ackPayloads
            .filter { it.cmdId == ActionsCmd.GET_SOURCE.id }
            .mapNotNull { ack -> if (ack.payload.isNotEmpty()) SourceType.fromId(ack.payload[0].toInt()) else null }
            .onEach { _currentSource.value = it }
            .launchIn(scope)

        // Listen to ACKs for connected sources
        protocol.ackPayloads
            .filter { it.cmdId == ActionsCmd.GET_CONNECTED_SOURCES.id }
            .mapNotNull { ack -> if (ack.payload.isNotEmpty()) SourceType.fromConnectedSourcesBitmask(ack.payload[0].toInt()) else null }
            .onEach { _connectedSources.value = it }
            .launchIn(scope)

        // Listen to notifications for connected sources
        protocol.notificationPayloads
            .filter { notif ->
                notif.cmdId == ActionsCmd.EVENT_NOTIFICATION.id &&
                notif.payload.isNotEmpty() &&
                notif.payload[0] == ActionsNotification.CONNECTED_SOURCES.id.toByte()
            }
            .mapNotNull { notif ->
                if (notif.payload.size >= 3) {
                    // Notification sends big-endian: [id, high, low]
                    val bitmask = ((notif.payload[1].toInt() and 0xFF) shl 8) or (notif.payload[2].toInt() and 0xFF)
                    SourceType.fromConnectedSourcesBitmask(bitmask)
                } else null
            }
            .onEach { _connectedSources.value = it }
            .launchIn(scope)

        // Register for notifications
        scope.launch {
            protocol.ensureNotificationRegistered(ActionsNotification.CONNECTED_SOURCES.id.toByte())
        }

        // Initial fetch + polling
        scope.launch {
            delay(200)
            fetch()
            // Poll current source every 5 seconds
            while (true) {
                delay(5_000)
                protocol.sendCommandNonBlocking(ActionsCmd.GET_SOURCE.id, byteArrayOf())
            }
        }
        scope.launch {
            delay(300)
            // Poll connected sources every 7.5 seconds
            while (true) {
                delay(7_500)
                protocol.sendCommandNonBlocking(ActionsCmd.GET_CONNECTED_SOURCES.id, byteArrayOf())
            }
        }
    }

    override suspend fun fetch(): Boolean {
        val currentOk = protocol.sendCommandNonBlocking(ActionsCmd.GET_SOURCE.id, byteArrayOf())
        val connectedOk = protocol.sendCommandNonBlocking(ActionsCmd.GET_CONNECTED_SOURCES.id, byteArrayOf())
        return currentOk && connectedOk
    }

    override suspend fun setSource(source: SourceType, channel: Byte): Boolean {
        // Build payload: [channel, source]
        val payload = byteArrayOf(channel, source.id)
        _currentSource.value = source
        return protocol.sendCommand(ActionsCmd.SET_SOURCE.id, payload)
    }

    /**
     * Cancels the coroutine scope to prevent memory leaks.
     * Called when the device disconnects or the session is cleared.
     */
    override fun onCleanup() {
        scope.cancel()
    }
}