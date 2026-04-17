package de.teufel.openmynd.modules.core.feature.eqgain.actions

import co.touchlab.kermit.Logger
import de.teufel.openmynd.modules.core.feature.EqBand
import de.teufel.openmynd.modules.core.feature.EqGain
import de.teufel.openmynd.modules.core.feature.base.FeatureLifecycle
import de.teufel.openmynd.modules.core.feature.eqgain.EqGainFeature
import de.teufel.openmynd.modules.core.protocol.AckPayload
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd.Companion.GET_EQ_PARAMETER_GAIN
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd.Companion.SET_EQ_PARAMETER_GAIN
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ACTIONS implementation of [EqGainFeature].
 *
 * The protocol multiplexes all bands over a single command id (GET 0x090d / SET 0x090f) and
 * the GET ACK contains *only* the gain byte — the band index is **not** echoed back, so the
 * only way to associate a response with a band is request/response correlation. We therefore
 * cannot reuse [de.teufel.openmynd.modules.core.feature.base.BaseRangeFeature] per band: two
 * BaseRangeFeature instances filtering on the same cmdId both consume every ACK and end up
 * mirroring whichever band was queried last.
 *
 * Instead we serialize per-band GETs through [fetchMutex] and route the next 0x090d ACK to
 * the band whose request is in flight via [pendingAck].
 */
class ActionsEqGainFeature(
    private val protocol: ActionsProtocol,
    private val eqGain: EqGain
) : EqGainFeature, FeatureLifecycle {

    private val logger = Logger.withTag("ActionsEqGain")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override val gainRange: IntRange = eqGain.gainRange
    override val supportedBands: List<EqBand> = eqGain.bands

    private val _bandGains = MutableStateFlow<Map<EqBand, Int?>>(
        supportedBands.associateWith { null }
    )
    override val bandGains: StateFlow<Map<EqBand, Int?>> = _bandGains.asStateFlow()

    // Serializes Bass/Treble GET requests so the next ACK belongs to a known band. SETs go
    // through the same mutex so a fast set() during an in-flight fetch() cannot insert a
    // 0x090d echo (none expected in spec, but keep the lane single-writer for safety).
    private val fetchMutex = Mutex()

    // Set inside fetchMutex prior to sending; completed by the ack observer below. Only one
    // outstanding fetch is possible because of the mutex, so a single nullable slot suffices.
    private var pendingAck: CompletableDeferred<AckPayload>? = null

    init {
        // Single observer for GET ACKs. Routing lives here, not in the band parser, because
        // the spec does not echo the band index (specs/mynd.txt §"Get EQ Parameter Gain").
        protocol.ackPayloads
            .filter { it.cmdId == GET_EQ_PARAMETER_GAIN.id }
            .onEach { ack -> pendingAck?.complete(ack) }
            .launchIn(scope)
    }

    override suspend fun fetch(band: EqBand): Boolean = fetchMutex.withLock {
        val deferred = CompletableDeferred<AckPayload>()
        pendingAck = deferred
        try {
            val sent = protocol.sendCommand(
                GET_EQ_PARAMETER_GAIN.id,
                byteArrayOf(band.toIndex().toByte())
            )
            if (!sent) {
                logger.w { "GET EQ gain for $band: command failed to send/ACK" }
                return@withLock false
            }
            // sendCommand already waited for the ACK with success/failure semantics, but it
            // discards the payload. Use the observer-completed deferred to read the actual
            // gain byte. The ACK is normally already in the deferred by the time we arrive
            // here; the small timeout protects against rare scheduler races.
            val ack = withTimeoutOrNull(500) { deferred.await() }
            if (ack == null) {
                logger.w { "GET EQ gain for $band: ACK observer did not deliver payload" }
                return@withLock false
            }
            if (!ack.success) {
                logger.w { "GET EQ gain for $band: device reported failure" }
                return@withLock false
            }
            val raw = ack.payload.firstOrNull()
            if (raw == null) {
                logger.w { "GET EQ gain for $band: empty ACK payload" }
                return@withLock false
            }
            // Spec: signed byte = gain * gainMultiplier. Use Int division (truncates toward
            // zero) — protocol values are exact 1 dB steps so no rounding loss expected.
            val gain = (raw.toInt() / eqGain.gainMultiplier).coerceIn(gainRange)
            _bandGains.value = _bandGains.value.toMutableMap().also { it[band] = gain }
            return@withLock true
        } finally {
            pendingAck = null
        }
    }

    override suspend fun fetch(): Boolean {
        // Sequential per-band fetch keeps each ACK uniquely attributable. Returning the
        // overall success lets prefetch retry the whole feature only when something actually
        // failed (versus marking it done after a partially-successful read).
        var allOk = true
        for (band in supportedBands) {
            allOk = fetch(band) && allOk
        }
        return allOk
    }

    override suspend fun set(band: EqBand, gain: Int): Boolean = fetchMutex.withLock {
        val clamped = gain.coerceIn(gainRange)
        // Optimistic update so the UI does not snap back while the SET is in flight.
        _bandGains.value = _bandGains.value.toMutableMap().also { it[band] = clamped }
        val protocolGain = (clamped * eqGain.gainMultiplier).toByte()
        val ok = protocol.sendCommand(
            SET_EQ_PARAMETER_GAIN.id,
            byteArrayOf(band.toIndex().toByte(), protocolGain)
        )
        if (!ok) logger.w { "SET EQ gain for $band to $clamped dB failed" }
        ok
    }

    override fun onCleanup() {
        scope.cancel()
    }
}
