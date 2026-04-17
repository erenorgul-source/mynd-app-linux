package de.teufel.openmynd.modules.core.feature.upgrade

import co.touchlab.kermit.Logger
import de.teufel.openmynd.modules.core.bluetooth.connector.DeviceConnector
import de.teufel.openmynd.modules.core.feature.base.FeatureLifecycle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bridges the [ActionsOtaProtocol] to BLE via [DeviceConnector].
 *
 * Responsibilities:
 * - Subscribe to OTA read characteristic notifications and feed bytes to the protocol
 * - Receive write requests from the protocol, chunk by MTU, and write to OTA write characteristic
 * - Expose upgrade state as a [StateFlow] for the UI
 */
class ActionsUpgradeManager(
    private val deviceConnector: DeviceConnector
) : FeatureLifecycle {

    companion object {
        const val OTA_SERVICE_UUID = "e49a25f8-f69a-11e8-8eb2-f2801f1b9fd1"
        const val OTA_WRITE_CHARACTERISTIC_UUID = "e49a25e0-f69a-11e8-8eb2-f2801f1b9fd1"
        const val OTA_READ_CHARACTERISTIC_UUID = "e49a28e1-f69a-11e8-8eb2-f2801f1b9fd1"
        // iOS write-without-response payload is often <= 182 bytes (MTU 185 - 3).
        // Keep a conservative chunk size to avoid silent drops/stalls on iOS.
        private const val OTA_WRITE_CHUNK_SIZE = 180
    }

    private val logger = Logger.withTag("ActionsUpgradeManager")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _fotaState = MutableStateFlow<ActionsFotaState>(ActionsFotaState.IDLE)
    val fotaState: StateFlow<ActionsFotaState> = _fotaState.asStateFlow()

    private var selectedFileData: ByteArray? = null

    private var otaProtocol: ActionsOtaProtocol? = null
    private var monitorJob: Job? = null
    private var writeProcessorJob: Job? = null
    private val writeChannel = Channel<ByteArray>(Channel.UNLIMITED)

    /**
     * Called when the user picks a firmware file. Stores the file data and
     * transitions to FILE_SELECTED so the UI can show the "Start Upgrade" screen.
     */
    fun selectFile(fileName: String, fileData: ByteArray) {
        logger.i { "File selected: $fileName (${fileData.size} bytes)" }
        selectedFileData = fileData
        _fotaState.value = ActionsFotaState.FILE_SELECTED(fileName, fileData.size)
    }

    /**
     * Starts the upgrade using the previously selected file.
     */
    fun startUpgrade() {
        val data = selectedFileData
        if (data == null) {
            logger.e { "No file selected" }
            _fotaState.value = ActionsFotaState.FAILED
            return
        }
        OtaSessionState.setActive(true)
        prepareAndStartUpgrade(data)
    }

    private val otaListener = object : ActionsOtaProtocol.ActionsOtaListener {
        override fun onStatus(state: Int) {
            _fotaState.value = when (state) {
                ActionsOtaProtocol.STATE_UNKNOWN -> ActionsFotaState.UNKNOWN
                ActionsOtaProtocol.STATE_IDLE -> ActionsFotaState.IDLE
                ActionsOtaProtocol.STATE_PREPARING -> ActionsFotaState.PREPARING
                ActionsOtaProtocol.STATE_PREPARED -> {
                    otaProtocol?.upgrade()
                    ActionsFotaState.PREPARED
                }
                ActionsOtaProtocol.STATE_TRANSFERRING -> ActionsFotaState.TRANSFERRING(0)
                ActionsOtaProtocol.STATE_TRANSFERRED -> {
                    OtaSessionState.setActive(false)
                    ActionsFotaState.TRANSFERRED
                }
                else -> ActionsFotaState.UNKNOWN
            }
        }

        override fun onRemoteStatusReceived(status: RemoteStatus?) {
            logger.i { "Remote status: $status" }
        }

        override fun onProgress(progress: Int, total: Int) {
            if (total > 0) {
                val pct = (progress.toLong() * 100 / total).toInt().coerceIn(0, 100)
                _fotaState.value = ActionsFotaState.TRANSFERRING(pct, progress, total)
            }
        }

        override fun onError(errcode: Int, errmsg: String?) {
            logger.e { "OTA error: $errcode $errmsg" }
            OtaSessionState.setActive(false)
            _fotaState.value = ActionsFotaState.FAILED
        }

        override fun onWriteBytes(count: Int) {
            // optional throughput tracking
        }
    }

    fun prepareAndStartUpgrade(fileData: ByteArray) {
        logger.i { "prepareAndStartUpgrade, file size: ${fileData.size}" }

        val protocol = ActionsOtaProtocol(onWriteBytes = { payload ->
            writeChannel.trySend(payload)
        })
        protocol.setListener(otaListener)
        otaProtocol = protocol

        if (!protocol.setOtaFile(fileData)) {
            logger.e { "Failed to set OTA file" }
            OtaSessionState.setActive(false)
            _fotaState.value = ActionsFotaState.FAILED
            return
        }

        val version = protocol.otaVersion
        if (version == null) {
            logger.e { "Failed to read OTA version" }
            OtaSessionState.setActive(false)
            _fotaState.value = ActionsFotaState.FAILED
            return
        }
        logger.i { "OTA version: $version" }

        _fotaState.value = ActionsFotaState.PREPARING

        scope.launch {
            startBleWriteProcessor()

            val subscriptionReady = startMonitoringResponses()
            val subscribed = withTimeoutOrNull(5000) { subscriptionReady.await() } ?: false
            if (!subscribed) {
                logger.e { "Failed to subscribe to OTA notifications — characteristic not found or timeout" }
                OtaSessionState.setActive(false)
                _fotaState.value = ActionsFotaState.FAILED
                return@launch
            }

            // Allow the BLE stack to finish the CCCD descriptor write
            delay(500)

            logger.i { "OTA notification subscription ready, starting handshake" }
            protocol.prepare(scope)
        }

        // Separate timeout: if still PREPARING after 20s total, the device didn't respond
        scope.launch {
            delay(20_000)
            if (_fotaState.value is ActionsFotaState.PREPARING) {
                logger.e { "Handshake timeout — device did not respond" }
                OtaSessionState.setActive(false)
                _fotaState.value = ActionsFotaState.FAILED
            }
        }
    }

    fun disconnectAndRelease() {
        logger.i { "disconnectAndRelease" }
        OtaSessionState.setActive(false)
        monitorJob?.cancel()
        monitorJob = null
        writeProcessorJob?.cancel()
        writeProcessorJob = null
        otaProtocol?.release()
        otaProtocol = null
        selectedFileData = null
        _fotaState.value = ActionsFotaState.IDLE
    }

    override fun onCleanup() {
        disconnectAndRelease()
        scope.cancel()
    }

    private fun startMonitoringResponses(): CompletableDeferred<Boolean> {
        val ready = CompletableDeferred<Boolean>()
        monitorJob?.cancel()
        monitorJob = scope.launch {
            try {
                logger.i { "Subscribing to OTA read characteristic ($OTA_READ_CHARACTERISTIC_UUID)..." }
                // subscribeToCharacteristic now suspends until the CCCD is live on the peer,
                // so once this returns we're actually subscribed and can safely signal ready.
                val flow = deviceConnector.subscribeToCharacteristic(
                    OTA_SERVICE_UUID,
                    OTA_READ_CHARACTERISTIC_UUID
                )
                logger.i { "OTA notification subscription ready" }
                if (!ready.isCompleted) ready.complete(true)
                var firstEmission = true
                flow.collect { data ->
                    if (firstEmission) {
                        logger.i { "First OTA notification received (${data.size} bytes)" }
                        firstEmission = false
                    }
                    logger.v { "OTA data received: ${data.size} bytes" }
                    otaProtocol?.feedData(data)
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to subscribe to OTA read characteristic" }
                if (!ready.isCompleted) ready.complete(false)
            }
        }
        return ready
    }

    private fun startBleWriteProcessor() {
        writeProcessorJob?.cancel()
        writeProcessorJob = scope.launch {
            logger.i { "BLE write processor started" }
            for (payload in writeChannel) {
                val maxPayload = OTA_WRITE_CHUNK_SIZE
                if (payload.size > maxPayload) {
                    val chunks = payload.asList().chunked(maxPayload)
                    logger.v { "Sending ${payload.size} bytes in ${chunks.size} chunks (chunkSize=$maxPayload)" }
                    for (chunk in chunks) {
                        val chunkBytes = chunk.toByteArray()
                        val ok = deviceConnector.writeCharacteristicWithoutResponse(
                            OTA_SERVICE_UUID,
                            OTA_WRITE_CHARACTERISTIC_UUID,
                            chunkBytes
                        )
                        if (!ok) {
                            logger.e { "Failed to write OTA chunk (${chunkBytes.size} bytes)" }
                            return@launch
                        }
                    }
                } else {
                    val ok = deviceConnector.writeCharacteristicWithoutResponse(
                        OTA_SERVICE_UUID,
                        OTA_WRITE_CHARACTERISTIC_UUID,
                        payload
                    )
                    if (!ok) {
                        logger.e { "Failed to write OTA payload (${payload.size} bytes)" }
                    } else {
                        logger.v { "Wrote OTA payload (${payload.size} bytes)" }
                    }
                }
            }
            logger.i { "BLE write processor stopped" }
        }
    }
}
