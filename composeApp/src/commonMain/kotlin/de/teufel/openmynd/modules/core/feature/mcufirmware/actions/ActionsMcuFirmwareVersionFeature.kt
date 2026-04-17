package de.teufel.openmynd.modules.core.feature.mcufirmware.actions

import de.teufel.openmynd.modules.core.feature.McuFirmwareVersion
import de.teufel.openmynd.modules.core.feature.base.FeatureLifecycle
import de.teufel.openmynd.modules.core.feature.mcufirmware.McuFirmwareVersionFeature
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import co.touchlab.kermit.Logger

/**
 * Actions protocol implementation of McuFirmwareVersionFeature.
 * Handles getting the MCU and DSP firmware versions.
 * 
 * NOTE: This feature requires complex custom logic including multiple targets, custom timeouts,
 * request throttling, and sequential request handling. It uses a custom implementation rather
 * than the simplified base classes.
 */
@OptIn(ExperimentalTime::class)
class ActionsMcuFirmwareVersionFeature(
    private val protocol: ActionsProtocol,
    mcuFirmwareVersion: McuFirmwareVersion = McuFirmwareVersion()
) : McuFirmwareVersionFeature, FeatureLifecycle {
    private val logger = Logger.withTag("McuFwFeature")
    // Hard-code the protocol-specific target mapping
    override val targets = mapOf("MCU" to 0x01.toByte())
        .let {
            if (mcuFirmwareVersion.builtInDsp) {
                it + ("DSP" to 0x00.toByte())
            } else {
                it
            }
        }

    private val _mcuVersion = MutableStateFlow<String?>(null)
    override val mcuVersion = _mcuVersion.asStateFlow()
    
    private val _dspVersion = MutableStateFlow<String?>(null)
    override val dspVersion = _dspVersion.asStateFlow()
    
    // Track pending requests by target
    private val pendingRequests = mutableMapOf<Byte, String>()
    private val requestMutex = Mutex()
    private var currentPendingTarget: Byte? = null
    
    // Track last fetch times
    private val lastFetchTimes = mutableMapOf<Byte, Long>()
    private val FETCH_THROTTLE_MS = 3000L  // Minimum time between fetches

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        logger.d { "Setting up MCU firmware version listeners" }
        // Handle GET_MCU_FIRMWARE_VERSION ACK responses
        protocol.ackPayloads
            .filter { it.cmdId == ActionsCmd.GET_MCU_FIRMWARE_VERSION.id }
            .mapNotNull { ack ->
                logger.v { "Received ACK payload: ${ack.payload.joinToString(" ") { "${it.toInt() and 0xFF}".padStart(2, '0') }}" }
                
                // Expected payload per spec: [major(1), minor(1), patch(1)]
                if (ack.payload.size >= 3) {
                    val major = ack.payload[0].toInt() and 0xFF
                    val minor = ack.payload[1].toInt() and 0xFF
                    val patch = ack.payload[2].toInt() and 0xFF
                    val versionStr = "$major.$minor.$patch"

                    // Attribute to the currently pending target (requests are sequential)
                    val target: Byte? = requestMutex.withLock {
                        val tgt = currentPendingTarget ?: pendingRequests.keys.firstOrNull()
                        if (tgt != null) {
                            pendingRequests.remove(tgt)
                            lastFetchTimes[tgt] = Clock.System.now().toEpochMilliseconds()
                            currentPendingTarget = null
                        }
                        tgt
                    }

                    if (target == null) {
                        logger.w { "No pending target to attribute MCU FW version response" }
                        null
                    } else {
                        logger.d { "Parsed firmware version for target $target: $versionStr" }
                        Pair(target, versionStr)
                    }
                } else {
                    logger.w { "Payload too short to parse version" }
                    null
                }
            }
            .onEach { (target, versionStr) ->
                // Look up target by value to handle any target ID
                val targetEntry = targets.entries.find { it.value == target }
                if (targetEntry != null) {
                    val targetKey = targetEntry.key
                    when (targetKey) {
                        "MCU" -> {
                            _mcuVersion.value = versionStr
                            logger.i { "Updated MCU firmware version to: $versionStr" }
                        }
                        "DSP" -> {
                            _dspVersion.value = versionStr
                            logger.i { "Updated DSP firmware version to: $versionStr" }
                        }
                        else -> {
                            logger.w { "Unknown target key: $targetKey" }
                        }
                    }
                } else {
                    logger.w { "Unknown target ID: $target" }
                }
            }
            .launchIn(scope)

        // Fetch initial values a bit later to avoid colliding with connection burst
        scope.launch {
            delay(400)
            fetch()
        }
    }

    /**
     * Fetch all firmware versions for both DSP and MCU targets with retry logic and prioritization.
     */
    override suspend fun fetch(): Boolean {
        logger.d { "Fetching all firmware versions" }
        var success = true
        
        // Clear any existing pending requests
        requestMutex.withLock {
            pendingRequests.clear()
        }
        
        // Process targets as sequential batches to avoid duplicate command issues
        val targetEntries = targets.entries.toList()
        
        for ((index, entry) in targetEntries.withIndex()) {
            val targetKey = entry.key
            val targetByte = entry.value
            
            // Check if target has been fetched recently
            val currentTime = Clock.System.now().toEpochMilliseconds()
            val lastFetchTime = lastFetchTimes[targetByte] ?: 0L
            val timeSinceLastFetch = currentTime - lastFetchTime
            
            if (timeSinceLastFetch < FETCH_THROTTLE_MS) {
                logger.v { "Using cached version for $targetKey, fetched ${timeSinceLastFetch}ms ago" }
                continue
            }
            
            // Register this request with the mutex
            requestMutex.withLock {
                pendingRequests[targetByte] = targetKey
                currentPendingTarget = targetByte
            }
            
            logger.d { "Fetching firmware version for $targetKey (target: $targetByte)" }
            val payload = byteArrayOf(targetByte)
            
            // Always use HIGH priority for firmware version requests
            val result = protocol.sendCommandWithTimeout(
                ActionsCmd.GET_MCU_FIRMWARE_VERSION.id,
                payload,
                timeoutMs = 3_000L
            )
            
            if (!result) {
                logger.w { "Failed to fetch $targetKey firmware version" }
                success = false
                requestMutex.withLock {
                    pendingRequests.remove(targetByte)
                }
            } else {
                logger.v { "Successfully requested firmware version for $targetKey" }
            }
            
            // Wait at least 700ms between requests to avoid duplicate command detection
            // This is longer than the duplicateCommandTimeout (500ms) in ActionsProtocol
            delay(700)
        }
        
        logger.i { "Firmware version fetch complete - MCU: ${mcuVersion.value}, DSP: ${dspVersion.value}" }
        return success
    }

    /**
     * Cancels the coroutine scope to prevent memory leaks.
     * Called when the device disconnects or the session is cleared.
     */
    override fun onCleanup() {
        scope.cancel()
    }
} 