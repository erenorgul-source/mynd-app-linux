package de.teufel.openmynd.modules.core.feature.upgrade

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pure-KMP port of the Actions OTA protocol state machine.
 *
 * Replaces Java IO (RandomAccessFile, PipedInputStream, Thread) with
 * ByteArray random access, Channel-based byte reading, and coroutines.
 */
@OptIn(ExperimentalStdlibApi::class)
class ActionsOtaProtocol(
    private val onWriteBytes: (payload: ByteArray) -> Unit
) {
    private val logger = Logger.withTag("ActionsOtaProtocol")

    private var running = false
    private var processJob: Job? = null

    private var otaFileData: ByteArray? = null
    private var otaFileLength: Int = 0

    private var state = STATE_IDLE
    private var fwVersion = "1.01"
    private val otaMode = 0
    private val vRAM = ByteArray(16)
    private var waitTimeout = 0
    private var restartTimeout = 0
    private var otaUnit = FRAME_SIZE
    private var interval = 0
    private var ackEnable = 0
    private var writeBytes = 0
    private var remoteCrcSupport = false

    private var listener: ActionsOtaListener? = null

    private val incomingData = Channel<ByteArray>(Channel.UNLIMITED)
    private val byteReader = ByteChannelReader(incomingData)

    interface ActionsOtaListener {
        fun onStatus(state: Int)
        fun onRemoteStatusReceived(status: RemoteStatus?)
        fun onProgress(progress: Int, total: Int)
        fun onError(errcode: Int, errmsg: String?)
        fun onWriteBytes(count: Int)
    }

    fun setListener(listener: ActionsOtaListener?) {
        this.listener = listener
    }

    fun setOtaFile(fileData: ByteArray): Boolean {
        if (fileData.isEmpty()) return false
        otaFileData = fileData
        otaFileLength = fileData.size
        logger.v { "setOtaFile length: $otaFileLength" }
        return true
    }

    val otaVersion: String?
        get() {
            val data = otaFileData ?: return null
            try {
                val text = data.decodeToString()
                val startTag = "<version_name>"
                val endTag = "</version_name>"
                val startIdx = text.indexOf(startTag)
                val endIdx = text.indexOf(endTag)
                if (startIdx >= 0 && endIdx > startIdx) {
                    fwVersion = text.substring(startIdx + startTag.length, endIdx)
                    logger.v { "fwVersion: $fwVersion" }
                    return fwVersion
                }
                return fwVersion
            } catch (e: Exception) {
                logger.e(e) { "Failed to read Fw version" }
                return null
            }
        }

    fun feedData(data: ByteArray) {
        incomingData.trySend(data)
    }

    fun prepare(scope: CoroutineScope) {
        running = true
        processJob = scope.launch { processLoop() }
        handshake()
    }

    fun upgrade() {
        notifyProgress(0, otaFileLength)
        requestRemoteParameters()
        writeBytes = 0
    }

    fun release() {
        running = false
        processJob?.cancel()
        processJob = null
        otaFileData = null
    }

    // --- Protocol command builders ---

    private fun assembleTLV(type: Int, length: Int, value: ByteArray?): ByteArray {
        var index = 0
        val buffer = if (value == null) ByteArray(1 + 2) else ByteArray(1 + 2 + value.size)
        buffer[index++] = type.toByte()
        buffer[index++] = length.toByte()
        buffer[index++] = (length shr 8).toByte()
        if (value != null && value.isNotEmpty()) {
            value.copyInto(buffer, destinationOffset = index)
        }
        return buffer
    }

    private fun assembleCommand(serverID: Int, commandID: Int, subTLVs: List<ByteArray>?): ByteArray {
        var subTLVsLen = 0
        subTLVs?.forEach { subTLVsLen += it.size }

        val superTLV = assembleTLV(0x80, subTLVsLen, null)
        val buffer = ByteArray(1 + 1 + superTLV.size + subTLVsLen)
        var index = 0
        buffer[index++] = serverID.toByte()
        buffer[index++] = commandID.toByte()
        superTLV.copyInto(buffer, destinationOffset = index)
        index += superTLV.size
        subTLVs?.forEach { tlv ->
            tlv.copyInto(buffer, destinationOffset = index)
            index += tlv.size
        }
        return buffer
    }

    private fun handshake() {
        if (listener == null) return
        notifyStatus(STATE_PREPARING)
        checkRemoteStatus(fwVersion, otaMode)
    }

    private fun checkRemoteStatus(fwVersion: String, mode: Int) {
        val tlvs = mutableListOf<ByteArray>()
        tlvs.add(assembleTLV(0x01, fwVersion.length, fwVersion.encodeToByteArray()))
        tlvs.add(assembleTLV(0x02, 0x02, byteArrayOf(0x00, 0x00)))
        tlvs.add(assembleTLV(0x03, vRAM.size, vRAM))
        tlvs.add(assembleTLV(0x04, 0x01, byteArrayOf(mode.toByte())))
        tlvs.add(assembleTLV(0x09, 0x01, byteArrayOf(0x01)))
        send(assembleCommand(0x09, 0x01, tlvs))
    }

    private fun requestRemoteParameters() {
        send(assembleCommand(0x09, 0x02, null))
    }

    private fun notifyRemoteAppReady(state: Int) {
        val tlvs = mutableListOf(assembleTLV(0x01, 0x01, byteArrayOf(state.toByte())))
        send(assembleCommand(0x09, 0x09, tlvs))
    }

    // --- State machine ---

    private suspend fun processLoop() {
        logger.v { "processLoop started" }
        while (running) {
            try {
                val serviceId = byteReader.readByte()
                if (serviceId != 0x09) continue

                val commandId = byteReader.readByte()
                logger.v { "processLoop command: 0x09 0x${commandId.toString(16)}" }

                when (commandId) {
                    0x01 -> handleCheckRemoteStatusResponse()
                    0x02 -> handleRequestParametersResponse()
                    0x03 -> handleDataTransferRequest()
                    0x05 -> handlePackageValidSize()
                    0x06 -> handleConfirmUpdateAndReboot()
                    0x07 -> handleErrorResponse()
                }
            } catch (_: CancellationException) {
                break
            } catch (e: Exception) {
                handleException(e)
            }
        }
        logger.v { "processLoop exited" }
    }

    private suspend fun readSubTLVs(): ByteArray? {
        val superTLV = byteReader.readBytes(3)
        val subTLVsLen = (superTLV[1].toInt() and 0xFF) + ((superTLV[2].toInt() and 0xFF) shl 8)
        if (subTLVsLen <= 0) return ByteArray(0)

        return withTimeoutOrNull(5000) {
            byteReader.readBytes(subTLVsLen)
        }
    }

    private suspend fun handleCheckRemoteStatusResponse() {
        val tlvs = readSubTLVs()
        if (tlvs == null || tlvs.size < 7) return
        var index = 0
        if (tlvs[index].toInt() != 0x7F) return
        index += 3
        val errCode = tlvs.readIntLE(index)
        index += 4

        logger.v { "0x0901 response errCode: $errCode" }

        var versionName: String? = null
        var boardName: String? = null
        var hardwareRev: String? = null
        var batteryThreshold = 30
        var versionCode = 0
        var featureSupport = 0

        while (index < tlvs.size) {
            val type = tlvs[index++].toInt()
            var len = (tlvs[index++].toInt() and 0xFF)
            len += (tlvs[index].toInt() and 0xFF) shl 8
            index++
            when (type) {
                0x04 -> { batteryThreshold = tlvs[index++].toInt(); }
                0x05 -> { versionName = tlvs.copyOfRange(index, index + len).decodeToString(); index += len }
                0x06 -> { boardName = tlvs.copyOfRange(index, index + len).decodeToString(); index += len }
                0x07 -> { hardwareRev = tlvs.copyOfRange(index, index + len).decodeToString(); index += len }
                0x08 -> { versionCode = tlvs.readIntLE(index); index += 4 }
                0x09 -> { featureSupport = tlvs[index++].toInt(); remoteCrcSupport = featureSupport and 0x01 == 0x01 }
                else -> index += len
            }
        }
        val status = RemoteStatus(versionName, boardName, hardwareRev, batteryThreshold, versionCode, featureSupport)

        if (errCode == 100000) {
            listener?.onRemoteStatusReceived(status)
            notifyStatus(STATE_PREPARED)
        } else {
            logger.e { "0x0901 Error: $errCode" }
            listener?.onError(errCode, MESSAGE_UNKNOWN)
        }
    }

    private suspend fun handleRequestParametersResponse() {
        val tlvs = readSubTLVs()
        if (tlvs == null || tlvs.size < 24) return

        var index = 3
        waitTimeout = tlvs.readIntLE(index, 2); index += 2
        index += 3
        restartTimeout = tlvs.readIntLE(index, 2); index += 2
        index += 3
        otaUnit = tlvs.readIntLE(index, 2); index += 2
        index += 3
        interval = tlvs.readIntLE(index, 2); index += 2
        index += 3
        ackEnable = tlvs[index].toInt()

        logger.v { "Parameters: otaUnit=$otaUnit, interval=$interval, ackEnable=$ackEnable" }
        notifyRemoteAppReady(1)
    }

    private suspend fun handleDataTransferRequest() {
        notifyStatus(STATE_TRANSFERRING)
        val tlvs = readSubTLVs()
        if (tlvs == null || tlvs.size < 14) return

        if (ackEnable == 1) {
            val buffer = ByteArray(5 + tlvs.size)
            buffer[0] = 0x09
            buffer[1] = 0x03
            buffer[2] = 0x80.toByte()
            buffer[3] = tlvs.size.toByte()
            buffer[4] = (tlvs.size shr 8).toByte()
            tlvs.copyInto(buffer, destinationOffset = 6)
            writeBuffer(buffer)
        }

        var index = 3
        val offset = tlvs.readIntLE(index); index += 4
        index += 3
        val length = tlvs.readIntLE(index); index += 4

        if (tlvs.size > 17) {
            index++
            val len = tlvs.readIntLE(index, 2); index += 2
            if (tlvs.size >= 17 + len) {
                val bitmap = ByteArray(len)
                for (i in 0 until len) {
                    bitmap[i] = tlvs[index++]
                }
                val frames = readFile(offset, length, bitmap)
                if (frames == null) {
                    logger.v { "OTA file does not exist!" }
                    return
                }
                for (i in frames.indices) {
                    val contentLen = frames[i].size
                    val buffer: ByteArray
                    if (remoteCrcSupport) {
                        buffer = ByteArray(6 + 4 + contentLen)
                        buffer[0] = 0x09
                        buffer[1] = 0x0B
                        buffer[2] = 0x80.toByte()
                        buffer[3] = (contentLen + 1 + 4).toByte()
                        buffer[4] = ((contentLen + 1 + 4) shr 8).toByte()
                        buffer[5] = (i % 256).toByte()
                        val checksum = crc32(frames[i])
                        buffer[6] = checksum.toByte()
                        buffer[7] = (checksum shr 8).toByte()
                        buffer[8] = (checksum shr 16).toByte()
                        buffer[9] = (checksum shr 24).toByte()
                        frames[i].copyInto(buffer, destinationOffset = 10)
                    } else {
                        buffer = ByteArray(6 + contentLen)
                        buffer[0] = 0x09
                        buffer[1] = 0x04
                        buffer[2] = 0x80.toByte()
                        buffer[3] = (contentLen + 1).toByte()
                        buffer[4] = ((contentLen + 1) shr 8).toByte()
                        buffer[5] = (i % 256).toByte()
                        frames[i].copyInto(buffer, destinationOffset = 6)
                    }
                    writeBuffer(buffer)
                    writeBytes += buffer.size
                    listener?.onWriteBytes(writeBytes)
                }
                notifyProgress(offset + length, otaFileLength)
            }
        }
    }

    private suspend fun handlePackageValidSize() {
        val tlvs = readSubTLVs()
        if (tlvs == null || tlvs.size < 14) return
        var index = 3
        val pkgValidSize = tlvs.readIntLE(index); index += 4
        index += 3
        val receivedSize = tlvs.readIntLE(index)
        logger.v { "0x0905 pkgValidSize: $pkgValidSize, receivedSize: $receivedSize" }
    }

    private suspend fun handleConfirmUpdateAndReboot() {
        val tlvs = readSubTLVs()
        if (tlvs == null || tlvs.size < 4) return
        val valid = tlvs[3].toInt()
        val buffer = assembleCommand(0x09, 0x06, null)
        if (valid == 1) {
            listener?.onStatus(STATE_TRANSFERRED)
        } else {
            listener?.onError(PACKAGE_INVALID, MESSAGE_PACKAGE_INVALID)
        }
        writeBuffer(buffer)
    }

    private suspend fun handleErrorResponse() {
        val tlvs = readSubTLVs()
        if (tlvs == null || tlvs.size < 7) return
        var index = 0
        if (tlvs[index++].toInt() == 0x7F) {
            index += 2
            val errCode = tlvs.readIntLE(index)
            if (errCode != 100000) {
                logger.e { "0x0907 Error: $errCode" }
                listener?.onError(errCode, MESSAGE_UNKNOWN)
            }
        }
    }

    // --- File reading ---

    private fun readFile(offset: Int, length: Int, bitmap: ByteArray): List<ByteArray>? {
        val fileData = otaFileData ?: return null
        val pkgIdx = BitmapUtils.getZeroBitIndexMap(bitmap)
        val packages = mutableListOf<ByteArray>()
        for (i in pkgIdx.indices) {
            val odd = length - pkgIdx[i] * otaUnit
            if (odd <= 0) break
            val o = offset + pkgIdx[i] * otaUnit
            val len = if (odd > otaUnit) otaUnit else odd
            if (o + len > fileData.size) break
            packages.add(fileData.copyOfRange(o, o + len))
        }
        return packages
    }

    // --- Helpers ---

    private fun notifyStatus(newState: Int) {
        logger.i { "notify status $newState" }
        if (this.state == newState) return
        this.state = newState
        listener?.onStatus(this.state)
    }

    private fun notifyProgress(progress: Int, total: Int) {
        logger.i { "progress: $progress / $total" }
        listener?.onProgress(progress, total)
    }

    private fun send(commandBytes: ByteArray) {
        if (!running) return
        try {
            writeBuffer(commandBytes)
        } catch (e: Exception) {
            handleException(e)
        }
    }

    private fun writeBuffer(buffer: ByteArray) {
        onWriteBytes(buffer)
    }

    private fun handleException(e: Exception) {
        logger.e(e) { "Exception in OTA protocol" }
        if (running) {
            listener?.onError(0, MESSAGE_UNKNOWN)
        }
        running = false
    }

    companion object {
        const val STATE_UNKNOWN = 0
        const val STATE_IDLE = 1
        const val STATE_PREPARING = 2
        const val STATE_PREPARED = 3
        const val STATE_TRANSFERRING = 4
        const val STATE_TRANSFERRED = 5
        private const val FRAME_SIZE = 256
        const val PACKAGE_INVALID = 1
        const val MESSAGE_UNKNOWN = "Unknown error"
        const val MESSAGE_PACKAGE_INVALID = "OTA package invalid, exit ota mode."
    }
}

// --- Pure-Kotlin helpers ---

/**
 * Read a little-endian integer of [length] bytes from [index].
 */
internal fun ByteArray.readIntLE(index: Int, length: Int = 4): Int {
    var result = 0
    for (i in 0 until length) {
        result = result or ((this[index + i].toInt() and 0xFF) shl (8 * i))
    }
    return result
}

/**
 * Coroutine-based byte reader that buffers incoming ByteArray chunks
 * and provides suspend-based sequential byte access.
 */
internal class ByteChannelReader(private val channel: Channel<ByteArray>) {
    private var currentChunk: ByteArray? = null
    private var position = 0

    suspend fun readByte(): Int {
        while (true) {
            val chunk = currentChunk
            if (chunk != null && position < chunk.size) {
                return chunk[position++].toInt() and 0xFF
            }
            currentChunk = channel.receive()
            position = 0
        }
    }

    suspend fun readBytes(count: Int): ByteArray {
        val result = ByteArray(count)
        for (i in 0 until count) {
            result[i] = readByte().toByte()
        }
        return result
    }
}

/**
 * Pure-Kotlin CRC32 implementation (ISO 3309 / ITU-T V.42 polynomial).
 */
internal fun crc32(data: ByteArray): Long {
    var crc = 0xFFFFFFFFL
    for (b in data) {
        crc = crc xor (b.toLong() and 0xFF)
        for (j in 0 until 8) {
            crc = if (crc and 1L == 1L) {
                (crc ushr 1) xor 0xEDB88320L
            } else {
                crc ushr 1
            }
        }
    }
    return crc xor 0xFFFFFFFFL
}
