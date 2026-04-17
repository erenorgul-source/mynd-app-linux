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
 * Configuration for selector-based commands (get + set by index).
 */
data class SelectorCommandConfig(
    val getCmd: UShort,
    val setCmd: UShort,
    val parseIndexFromAck: (ByteArray) -> Int?,
    val buildSetPayload: (Int) -> ByteArray = { byteArrayOf(it.toByte()) },
    val parseFromNotification: (NotificationPayload) -> Int? = { null }
)

/**
 * Reusable selector feature: choose one option by index (e.g. ANC modes).
 * Behavior adapts based on ValueRetrieval configuration.
 * 
 * Implements [FeatureLifecycle] to ensure proper cleanup of coroutine scope.
 */
class BaseSelectorFeature(
    private val protocol: ProtocolContext,
    private val command: SelectorCommandConfig,
    private val valueRetrieval: ValueRetrieval = ValueRetrieval.FetchingOnly,
    /** When true, a failed select() triggers a fetch() to restore value from device. */
    private val fetchOnSetFailure: Boolean = true,
    initialFetchDelayMs: Long = 250,
    retryConfig: RetryConfig = RetryConfig()
) : SelectorFeature<Int>, FeatureLifecycle {
    private val logger = Logger.withTag("BaseSelectorFeature")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _index = MutableStateFlow<Int?>(null)
    val index = _index.asStateFlow()
    override val selected = _index.asStateFlow()

    init {
        // Listen to ACK responses for GET commands
        if (shouldListenToAcks(valueRetrieval)) {
            protocol.ackPayloads
                .filter { it.cmdId == command.getCmd }
                .mapNotNull { command.parseIndexFromAck(it.payload) }
                .onEach { _index.value = it }
                .launchIn(scope)
        }

        scope.setupNotificationUpdates(
            protocol = protocol,
            valueRetrieval = valueRetrieval,
            parseFromNotification = command.parseFromNotification,
            onValue = { _index.value = it },
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
            hasValue = { index.value != null },
            fetch = ::fetch,
        )
    }

    override suspend fun fetch(): Boolean = protocol.sendCommand(command.getCmd, byteArrayOf())
    
    override suspend fun select(value: Int): Boolean {
        _index.value = value
        val ok = protocol.sendCommand(command.setCmd, command.buildSetPayload(value))
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


