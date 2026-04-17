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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlin.UShort

/**
 * Configuration for retry behavior when fetching values.
 */
data class RetryConfig(
    val enabled: Boolean = true,
    val initialDelayMs: Long = 400,
    val maxDelayMs: Long = 8000,
    val multiplier: Double = 1.8
)

/**
 * Configuration for a fetch command.
 */
data class FetchCommandConfig<T : Any>(
    val cmdId: UShort,
    val parseFromAck: (ByteArray) -> T?,
    val buildPayload: () -> ByteArray = { byteArrayOf() },
    val parseFromNotification: (NotificationPayload) -> T? = { null }
)

/**
 * Reusable read feature that adapts its behavior based on ValueRetrieval.
 *
 * - FetchingOnly: Only uses ACK responses from GET commands
 * - NotificationsOnly: Only subscribes to notifications (no initial fetch)
 * - NotificationsAndFetching: Uses both notifications and fetching with initial fetch
 *
 * Implements [FeatureLifecycle] to ensure proper cleanup of coroutine scope.
 */
class BaseReadFeature<T : Any>(
    private val protocol: ProtocolContext,
    private val command: FetchCommandConfig<T>,
    private val valueRetrieval: ValueRetrieval = ValueRetrieval.FetchingOnly,
    initialFetchDelayMs: Long = 120,
    retryConfig: RetryConfig = RetryConfig()
) : ReadFeature<T>, FeatureLifecycle {
    private val logger = Logger.withTag("BaseReadFeature")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _value = MutableStateFlow<T?>(null)
    override val value = _value.asStateFlow()

    init {
        // Listen to ACK responses for fetch commands
        if (shouldListenToAcks(valueRetrieval)) {
            protocol.ackPayloads
                .filter { it.cmdId == command.cmdId }
                .mapNotNull { command.parseFromAck(it.payload) }
                .onEach { _value.value = it }
                .launchIn(scope)
        }

        scope.setupNotificationUpdates(
            protocol = protocol,
            valueRetrieval = valueRetrieval,
            parseFromNotification = command.parseFromNotification,
            onValue = { _value.value = it },
            logger = logger,
        )

        scope.scheduleInitialFetch(
            valueRetrieval = valueRetrieval,
            initialFetchDelayMs = initialFetchDelayMs,
            fetch = ::fetch,
        )

        // Retry with backoff until first value obtained (for features that support fetching)
        scope.startRetryUntilFirstValue(
            valueRetrieval = valueRetrieval,
            retryConfig = retryConfig,
            hasValue = { value.value != null },
            fetch = ::fetch,
        )
    }

    override suspend fun fetch(): Boolean = protocol.sendCommand(command.cmdId, command.buildPayload())

    /**
     * Cancels the coroutine scope to prevent memory leaks.
     * Called when the device disconnects or the session is cleared.
     */
    override fun onCleanup() {
        scope.cancel()
    }
}

