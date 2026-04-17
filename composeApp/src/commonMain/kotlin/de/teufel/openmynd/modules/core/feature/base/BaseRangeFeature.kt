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
 * Configuration for range-based commands (get + set within a range).
 */
data class RangeCommandConfig(
    val getCmd: UShort,
    val setCmd: UShort,
    val parseFromAck: (ByteArray) -> Int?,
    val range: IntRange,
    val buildGetPayload: () -> ByteArray = { byteArrayOf() },
    val buildSetPayload: (Int) -> ByteArray = { byteArrayOf(it.toByte()) },
    val parseFromNotification: (NotificationPayload) -> Int? = { null }
)

/**
 * Reusable range feature: get and set a value within a fixed range.
 * Behavior adapts based on ValueRetrieval configuration.
 * 
 * Implements [FeatureLifecycle] to ensure proper cleanup of coroutine scope.
 */
class BaseRangeFeature(
    private val protocol: ProtocolContext,
    private val command: RangeCommandConfig,
    private val valueRetrieval: ValueRetrieval = ValueRetrieval.FetchingOnly,
    /** When true, a failed set() triggers a fetch() to restore value from device. */
    private val fetchOnSetFailure: Boolean = true,
    initialFetchDelayMs: Long = 250,
    retryConfig: RetryConfig = RetryConfig()
) : RangeFeature, FeatureLifecycle {
    private val logger = Logger.withTag("BaseRangeFeature")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    override val range: IntRange = command.range
    private val _value = MutableStateFlow<Int?>(null)
    override val value = _value.asStateFlow()

    init {
        // Listen to ACK responses for GET commands
        if (shouldListenToAcks(valueRetrieval)) {
            protocol.ackPayloads
                .filter { it.cmdId == command.getCmd }
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

        // Retry with backoff until first value obtained
        scope.startRetryUntilFirstValue(
            valueRetrieval = valueRetrieval,
            retryConfig = retryConfig,
            hasValue = { value.value != null },
            fetch = ::fetch,
        )
    }

    override suspend fun fetch(): Boolean = protocol.sendCommand(command.getCmd, command.buildGetPayload())
    
    override suspend fun set(value: Int): Boolean {
        val clamped = value.coerceIn(range)
        _value.value = clamped
        val ok = protocol.sendCommand(command.setCmd, command.buildSetPayload(clamped))
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


