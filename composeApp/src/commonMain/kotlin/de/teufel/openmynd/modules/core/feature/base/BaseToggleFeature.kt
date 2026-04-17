package de.teufel.openmynd.modules.core.feature.base

import co.touchlab.kermit.Logger
import de.teufel.openmynd.modules.core.protocol.NotificationPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlin.UShort

/**
 * Configuration for toggle-based commands (get status + enable/disable).
 */
data class ToggleCommandConfig(
    val getStatusCmd: UShort,
    val enableCmd: UShort,
    val disableCmd: UShort,
    val parseStatusFromAck: (ByteArray) -> Boolean?,
    val parseStatusFromNotification: (NotificationPayload) -> Boolean? = { null }
)

/**
 * Reusable toggle feature implementation (on/off).
 * Behavior adapts based on ValueRetrieval configuration.
 * 
 * Implements [FeatureLifecycle] to ensure proper cleanup of coroutine scope.
 */
class BaseToggleFeature(
    private val protocol: ProtocolContext,
    private val command: ToggleCommandConfig,
    private val valueRetrieval: ValueRetrieval = ValueRetrieval.FetchingOnly,
    /** When true, a failed enable/disable triggers a fetch() to restore value from device. */
    private val fetchOnSetFailure: Boolean = true,
    initialFetchDelayMs: Long = 250,
    retryConfig: RetryConfig = RetryConfig()
) : ToggleFeature, FeatureLifecycle {
    private val logger = Logger.withTag("BaseToggleFeature")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _enabled = MutableStateFlow<Boolean?>(null)
    override val enabled = _enabled.asStateFlow()

    init {
        // Listen to ACK responses for GET status commands
        if (shouldListenToAcks(valueRetrieval)) {
            protocol.ackPayloads
                .filter { it.cmdId == command.getStatusCmd }
                .mapNotNull { command.parseStatusFromAck(it.payload) }
                .onEach { _enabled.value = it }
                .launchIn(scope)
        }

        scope.setupNotificationUpdates(
            protocol = protocol,
            valueRetrieval = valueRetrieval,
            parseFromNotification = command.parseStatusFromNotification,
            onValue = { _enabled.value = it },
            logger = logger,
        )

        scope.scheduleInitialFetch(
            valueRetrieval = valueRetrieval,
            initialFetchDelayMs = initialFetchDelayMs,
            fetch = ::fetch,
        )

        // Retry with backoff until first value obtained
        scope.startRetryUntilFirstValue(
            valueRetrieval = valueRetrieval,
            retryConfig = retryConfig,
            hasValue = { enabled.value != null },
            fetch = ::fetch,
        )
    }

    override suspend fun fetch(): Boolean = protocol.sendCommand(command.getStatusCmd, byteArrayOf())
    override suspend fun set(enabled: Boolean): Boolean = setInternal(enabled)

    private suspend fun setInternal(value: Boolean): Boolean {
        _enabled.value = value
        val ok = if (value) {
            protocol.sendCommand(command.enableCmd, byteArrayOf())
        } else {
            protocol.sendCommand(command.disableCmd, byteArrayOf())
        }
        if (!ok && fetchOnSetFailure) fetch()
        return ok
    }

    /**
     * Cancels the coroutine scope to prevent memory leaks.
     * Called when the device disconnects or the session is cleared.
     */
    override fun onCleanup() {
        scope.cancel()
    }
}


