package de.teufel.openmynd.modules.core.feature.chargingstatus.actions

import de.teufel.openmynd.modules.core.feature.ChargingStatus
import de.teufel.openmynd.modules.core.feature.base.FeatureLifecycle
import de.teufel.openmynd.modules.core.feature.chargingstatus.ChargingStatusFeature
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import de.teufel.openmynd.modules.core.protocol.actions.ActionsNotification
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
 * Actions protocol implementation of ChargingStatusFeature.
 * NOTE: This feature requires custom polling and non-blocking sends, so it uses a custom implementation
 * rather than the simplified base classes.
 */
class ActionsChargingStatusFeature(
    private val protocol: ActionsProtocol,
    @Suppress("UNUSED_PARAMETER") definition: ChargingStatus = ChargingStatus
) : ChargingStatusFeature, FeatureLifecycle {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _isConnected = MutableStateFlow<Boolean?>(null)
    override val isConnected: StateFlow<Boolean?> = _isConnected.asStateFlow()

    init {
        // Listen to ACKs
        protocol.ackPayloads
            .filter { it.cmdId == ActionsCmd.GET_POWER_ADAPTER_STATUS.id }
            .mapNotNull { ack -> if (ack.payload.isNotEmpty()) ack.payload[0].toInt() == 0x01 else null }
            .onEach { _isConnected.value = it }
            .launchIn(scope)

        // Listen to notifications
        protocol.notificationPayloads
            .filter { notif ->
                notif.cmdId == ActionsCmd.EVENT_NOTIFICATION.id &&
                notif.payload.isNotEmpty() &&
                notif.payload[0] == ActionsNotification.POWER_ADAPTER_STATUS.id.toByte()
            }
            .mapNotNull { notif -> if (notif.payload.size >= 2) notif.payload[1].toInt() == 0x01 else null }
            .onEach { _isConnected.value = it }
            .launchIn(scope)

        // Register for notifications
        scope.launch {
            protocol.ensureNotificationRegistered(ActionsNotification.POWER_ADAPTER_STATUS.id.toByte())
        }

        // Initial fetch + polling (since charging status changes frequently)
        scope.launch {
            delay(150)
            fetch()
            // Poll every 2 seconds as fallback
            while (true) {
                delay(2_000)
                fetch()
            }
        }
    }

    override suspend fun fetch(): Boolean {
        // Use non-blocking to avoid delaying user actions
        return protocol.sendCommandNonBlocking(ActionsCmd.GET_POWER_ADAPTER_STATUS.id, byteArrayOf())
    }

    /**
     * Cancels the coroutine scope to prevent memory leaks.
     * Called when the device disconnects or the session is cleared.
     */
    override fun onCleanup() {
        scope.cancel()
    }
}