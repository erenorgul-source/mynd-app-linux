package de.teufel.openmynd.modules.core.protocol

import de.teufel.openmynd.modules.core.bluetooth.connector.DeviceConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Shared base for command/response style protocols.
 * Subclasses provide packet format, UUIDs and incoming parsing (emitting into provided flows).
 */
abstract class AbstractCommandProtocol(
    protected val deviceConnector: DeviceConnector
) : DeviceProtocol {

    protected val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var initialized: Boolean = false

    // Public streams.
    //
    // Buffers are sized generously because we now use suspending `emit` from the response
    // handler — under bursty load (e.g. iOS receiving battery + volume + sources +
    // sound-icons notifications within a single connection interval, or a flurry of
    // parallel initial fetches) we never want to silently drop an ACK. Each feature has
    // its own subscriber, so the slowest one dictates back-pressure; a larger buffer
    // means we almost never block the BLE stack.
    protected val _ackPayloads = MutableSharedFlow<AckPayload>(extraBufferCapacity = 256)
    override val ackPayloads: Flow<AckPayload> = _ackPayloads.asSharedFlow()

    protected val _notificationPayloads = MutableSharedFlow<NotificationPayload>(extraBufferCapacity = 256)
    override val notificationPayloads: Flow<NotificationPayload> = _notificationPayloads.asSharedFlow()

    // Command de-duplication and timing
    protected open val commandTimeoutMs: Long = 5_000L
    protected open val commandDebounceMs: Long = 200L
    protected val commandMutex = Mutex()
    protected val recentCommands: MutableMap<Any, Long> = mutableMapOf()

    enum class Priority { HIGH }

    override suspend fun initialize(): Boolean {
        if (initialized) return true
        val ok = onInitialize(scope)
        if (ok) initialized = true
        return ok
    }

    override suspend fun shutdown() {
        initialized = false
        scope.cancel()
    }

    override fun isReady(): Boolean = initialized

    /**
     * Enqueue a command and await its ACK with a timeout. Subclass must emit ACKs to [_ackPayloads].
     */
    @OptIn(ExperimentalTime::class)
    suspend fun sendInternalCommand(commandId: UShort, payload: ByteArray = byteArrayOf()): Boolean {
        if (!initialized) return false
        val key = duplicateCommandKey(commandId, payload)
        val now = Clock.System.now().toEpochMilliseconds()
        val last = recentCommands[key] ?: 0L
        if (now - last < commandDebounceMs) return false

        // serialize sends
        return commandMutex.withLock {
            recentCommands[key] = now
            val packet = buildPacket(commandId, payload)
            val writeOk = deviceConnector.writeCharacteristic(
                commandServiceUuid,
                commandCharacteristicUuid,
                packet
            )
            if (!writeOk) return@withLock false

            val start = Clock.System.now().toEpochMilliseconds()
            val ack = withTimeoutOrNullCompat(commandTimeoutMs) {
                ackPayloads.filter { it.cmdId == commandId }.first()
            }
            val elapsed = Clock.System.now().toEpochMilliseconds() - start
            val spacing = 20L
            if (elapsed < spacing) delay(spacing)
            return@withLock ack?.success ?: false
        }
    }

    /**
     * Unique key for duplicate suppression. Default uses commandId + payload hash.
     */
    protected open fun duplicateCommandKey(commandId: UShort, payload: ByteArray): Any =
        commandId to payload.contentHashCode()

    /**
     * Subclass should subscribe to incoming characteristic and emit into [_ackPayloads]/[_notificationPayloads].
     */
    protected abstract suspend fun onInitialize(scope: CoroutineScope): Boolean

    /** UUIDs for writing commands */
    protected abstract val commandServiceUuid: String
    protected abstract val commandCharacteristicUuid: String

    /** Build the packet to send for a given command and payload. */
    protected abstract fun buildPacket(commandId: UShort, payload: ByteArray): ByteArray
}

// Small helpers to avoid importing kotlinx.coroutines.withTimeout everywhere in subclasses
private suspend fun <T> withTimeoutOrNullCompat(timeMs: Long, block: suspend () -> T): T? =
    kotlinx.coroutines.withTimeoutOrNull(timeMs) { block() }


