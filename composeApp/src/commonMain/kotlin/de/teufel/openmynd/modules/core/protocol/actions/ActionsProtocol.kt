package de.teufel.openmynd.modules.core.protocol.actions

import co.touchlab.kermit.Logger
import de.teufel.openmynd.modules.core.bluetooth.connector.DeviceConnector
import de.teufel.openmynd.modules.core.protocol.AckPayload
import de.teufel.openmynd.modules.core.protocol.NotificationPayload
import de.teufel.openmynd.modules.core.feature.ActionsUpgrade
import de.teufel.openmynd.modules.core.feature.AutoOffTimer
import de.teufel.openmynd.modules.core.feature.Battery
import de.teufel.openmynd.modules.core.feature.BatteryCapacity
import de.teufel.openmynd.modules.core.feature.BatteryFriendlyCharging
import de.teufel.openmynd.modules.core.feature.ChargingStatus
import de.teufel.openmynd.modules.core.feature.DeviceColor
import de.teufel.openmynd.modules.core.feature.EcoMode
import de.teufel.openmynd.modules.core.feature.EqGain
import de.teufel.openmynd.modules.core.feature.Feature
import de.teufel.openmynd.modules.core.feature.FirmwareVersion
import de.teufel.openmynd.modules.core.feature.LedBrightness
import de.teufel.openmynd.modules.core.feature.MasterMute
import de.teufel.openmynd.modules.core.feature.MasterVolume
import de.teufel.openmynd.modules.core.feature.McuFirmwareVersion
import de.teufel.openmynd.modules.core.feature.Multipoint
import de.teufel.openmynd.modules.core.feature.PartyLinkBroadcast
import de.teufel.openmynd.modules.core.feature.SoundIcons
import de.teufel.openmynd.modules.core.feature.SourceSelection
import de.teufel.openmynd.modules.core.feature.upgrade.ActionsUpgradeManager
import de.teufel.openmynd.modules.core.feature.upgrade.OtaSessionState
import de.teufel.openmynd.modules.core.feature.autooff.actions.ActionsAutoOffTimerFeature
import de.teufel.openmynd.modules.core.feature.base.ProtocolContext
import de.teufel.openmynd.modules.core.feature.base.TeufelNotification
import de.teufel.openmynd.modules.core.feature.battery.actions.ActionsBatteryCapacityFeature
import de.teufel.openmynd.modules.core.feature.battery.actions.ActionsBatteryFeature
import de.teufel.openmynd.modules.core.feature.batteryfriendly.actions.ActionsBatteryFriendlyChargingFeature
import de.teufel.openmynd.modules.core.feature.chargingstatus.actions.ActionsChargingStatusFeature
import de.teufel.openmynd.modules.core.feature.devicecolor.actions.ActionsDeviceColorFeature
import de.teufel.openmynd.modules.core.feature.ecomode.actions.ActionsEcoModeFeature
import de.teufel.openmynd.modules.core.feature.eqgain.actions.ActionsEqGainFeature
import de.teufel.openmynd.modules.core.feature.firmwareversion.actions.ActionsFirmwareVersionFeature
import de.teufel.openmynd.modules.core.feature.ledbrightness.actions.ActionsLedBrightnessFeature
import de.teufel.openmynd.modules.core.feature.mastermute.actions.ActionsMasterMuteFeature
import de.teufel.openmynd.modules.core.feature.mastervolume.actions.ActionsMasterVolumeFeature
import de.teufel.openmynd.modules.core.feature.mcufirmware.actions.ActionsMcuFirmwareVersionFeature
import de.teufel.openmynd.modules.core.feature.multipoint.actions.ActionsMultipointFeature
import de.teufel.openmynd.modules.core.feature.partylinkbroadcast.actions.ActionsPartyLinkBroadcastFeature
import de.teufel.openmynd.modules.core.feature.soundicons.actions.ActionsSoundIconsFeature
import de.teufel.openmynd.modules.core.feature.sourceselection.actions.ActionsSourceSelectionFeature
import de.teufel.openmynd.modules.core.protocol.AbstractCommandProtocol
import de.teufel.openmynd.modules.core.protocol.DeviceProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Implementation of DeviceProtocol for Actions devices (e.g., MYND).
 * Manages protocol initialization and provides transport for commands and responses.
 */
@OptIn(ExperimentalStdlibApi::class, ExperimentalUnsignedTypes::class)
class ActionsProtocol(
    deviceConnector: DeviceConnector
) : AbstractCommandProtocol(deviceConnector), DeviceProtocol, ProtocolContext {

    companion object {
        const val ACTIONS_SERVICE_UUID = "00001100-d102-11e1-9b23-00025b00a5a5"
        const val ACTIONS_COMMAND_CHARACTERISTIC_UUID = "00001101-d102-11e1-9b23-00025b00a5a5"
        const val ACTIONS_RESPONSE_CHARACTERISTIC_UUID = "00001102-d102-11e1-9b23-00025b00a5a5"
        const val TEUFEL_VENDOR_ID: UShort = 0x2cc2u
        const val ACK_BIT: UShort = 0x8000u
    }

    // use inherited scope from base class
    private val duplicateCommandTimeout = 500L

    // Command priority levels
    enum class CommandPriority {
        HIGH
    }

    // Flows are provided by base class
    private val logger = Logger.withTag("ActionsProtocol")

    private fun shouldDeferForOta(commandId: UShort): Boolean {
        if (!OtaSessionState.active.value) return false
        // Keep command lane free while OTA owns BLE transport.
        logWithCount(
            "Skipping cmd=0x${commandId.toString(16)} while OTA is active",
            "ota_skip_${commandId.toString(16)}"
        )
        return true
    }

    // Track number of identical logs to reduce verbosity
    private val logCounters = mutableMapOf<String, Int>()
    private val maxLogRepeats = 3

    // Ensure we only register once per-notification id
    private val notificationRegistrationMutex = Mutex()
    private val registeredNotificationIds = mutableSetOf<Byte>()

    override suspend fun onInitialize(scope: CoroutineScope): Boolean {
        logger.i { "Initializing..." }
        return try {
            // Request larger MTU for larger packets
            logger.d { "Requesting MTU size..." }
            val mtuSuccess = deviceConnector.requestMtu(512)
            if (!mtuSuccess) logger.w { "MTU request failed, continuing with default MTU" }
            // Reduce artificial pause; GATT stack handles pacing
            delay(100)

            // Subscribe to response/notification characteristic. Suspends until the CCCD is
            // actually live on the peer so any commands we send afterwards are guaranteed to
            // have their ACKs delivered to us (was previously racing, which caused the first
            // few notification registrations to silently drop and left features zeroed out).
            logger.d { "Subscribing to Response characteristic..." }
            val responseFlow = deviceConnector.subscribeToCharacteristic(
                ACTIONS_SERVICE_UUID,
                ACTIONS_RESPONSE_CHARACTERISTIC_UUID
            )
            // Explicit readiness handshake: the launched collector signals when it has
            // actually subscribed to the upstream flow. Without this handshake we'd race
            // `scope.launch` scheduling against the next line issuing commands — commands
            // write before the collect is live, and any ACK that arrives before the collect
            // attaches is dropped (SharedFlow with replay=0 discards emissions that have
            // no subscribers).
            val collectorReady = CompletableDeferred<Unit>()
            scope.launch {
                try {
                    responseFlow
                        .onStart { collectorReady.complete(Unit) }
                        .collect { responseData ->
                            try { handleResponseOrNotification(responseData) }
                            catch (ex: Exception) { logger.e(ex) { "Error processing response/notification" } }
                        }
                    // Note: handleResponseOrNotification is a suspend function so back-pressure
                    // applies here all the way to subscribers; this preserves event ordering and
                    // avoids the silent ACK drops we'd get with tryEmit on a saturated buffer.
                } catch (e: Exception) {
                    if (!collectorReady.isCompleted) collectorReady.complete(Unit)
                    logger.e(e) { "Failed to subscribe to response characteristic" }
                }
            }
            withTimeoutOrNull(2000) { collectorReady.await() }
            // Eagerly register frequently used notifications to avoid sporadic misses (Android)
            try {
                ensureNotificationRegistered(ActionsNotification.BATTERY_LEVEL.id.toByte())
                ensureNotificationRegistered(ActionsNotification.CONNECTED_SOURCES.id.toByte())
                ensureNotificationRegistered(ActionsNotification.ECO_MODE_STATUS.id.toByte())
                ensureNotificationRegistered(ActionsNotification.MASTER_MUTE_STATUS.id.toByte())
                ensureNotificationRegistered(ActionsNotification.MASTER_VOLUME.id.toByte())
                ensureNotificationRegistered(ActionsNotification.POWER_ADAPTER_STATUS.id.toByte())
                ensureNotificationRegistered(ActionsNotification.SOUND_ICONS_STATUS.id.toByte())
            } catch (_: Throwable) { }
            logger.i { "Initialized." }
            true
        } catch (e: Exception) {
            logger.e(e) { "Initialization failed" }
            false
        }
    }

    override suspend fun shutdown() {
        // Clear registration cache on shutdown so a new connection can re-register
        notificationRegistrationMutex.withLock { registeredNotificationIds.clear() }
        super.shutdown()
    }

    override val commandServiceUuid: String = ACTIONS_SERVICE_UUID
    override val commandCharacteristicUuid: String = ACTIONS_COMMAND_CHARACTERISTIC_UUID

    /**
     * Enqueues a command and waits for its completion
     */
    override suspend fun sendCommand(
        cmdId: UShort,
        payload: ByteArray
    ): Boolean {
        if (shouldDeferForOta(cmdId)) return false
        return sendInternalCommand(cmdId, payload)
    }

    /**
     * Legacy overload for backward compatibility
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun sendCommand(
        commandId: UShort,
        payload: ByteArray = byteArrayOf(),
        priority: CommandPriority = CommandPriority.HIGH
    ): Boolean = sendCommand(commandId, payload)

    /**
     * Process commands from the queue one at a time, prioritizing higher priority commands
     */
    override val commandTimeoutMs: Long get() = 1_500L
    override val commandDebounceMs: Long get() = duplicateCommandTimeout

    override fun buildPacket(commandId: UShort, payload: ByteArray): ByteArray {
        val vendorIdBytes = byteArrayOf(((TEUFEL_VENDOR_ID.toInt() ushr 8) and 0xFF).toByte(), (TEUFEL_VENDOR_ID.toInt() and 0xFF).toByte())
        val commandIdBytes = byteArrayOf(((commandId.toInt() ushr 8) and 0xFF).toByte(), (commandId.toInt() and 0xFF).toByte())
        return vendorIdBytes + commandIdBytes + payload
    }

    /**
     * Best-effort, non-blocking send that will skip if the command queue is busy.
     * Intended for GET/status polling so user actions are not delayed.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun sendCommandNonBlocking(
        commandId: UShort,
        payload: ByteArray = byteArrayOf(),
        timeoutMs: Long = 1_500L
    ): Boolean {
        if (shouldDeferForOta(commandId)) return false
        if (!isReady()) return false
        val key = duplicateCommandKey(commandId, payload)
        val now = Clock.System.now().toEpochMilliseconds()
        val last = recentCommands[key] ?: 0L
        if (now - last < commandDebounceMs) return false

        val acquired = try { commandMutex.tryLock() } catch (_: Throwable) { false }
        if (!acquired) return false
        try {
            recentCommands[key] = now
            val packet = buildPacket(commandId, payload)
            val writeOk = deviceConnector.writeCharacteristic(
                ACTIONS_SERVICE_UUID,
                ACTIONS_COMMAND_CHARACTERISTIC_UUID,
                packet
            )
            if (!writeOk) return false

            val start = Clock.System.now().toEpochMilliseconds()
            val ack = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                ackPayloads.filter { it.cmdId == commandId }.first()
            }
            val elapsed = Clock.System.now().toEpochMilliseconds() - start
            val spacing = 20L
            if (elapsed < spacing) delay(spacing)
            return ack?.success ?: false
        } finally {
            try { commandMutex.unlock() } catch (_: Throwable) { }
        }
    }

    /**
     * Send a command with a custom ACK timeout. Use for commands known to respond slowly (e.g., MCU FW).
     */
    @OptIn(ExperimentalTime::class)
    suspend fun sendCommandWithTimeout(
        commandId: UShort,
        payload: ByteArray = byteArrayOf(),
        timeoutMs: Long
    ): Boolean {
        if (shouldDeferForOta(commandId)) return false
        if (!isReady()) return false
        val key = duplicateCommandKey(commandId, payload)
        val now = Clock.System.now().toEpochMilliseconds()
        val last = recentCommands[key] ?: 0L
        if (now - last < commandDebounceMs) return false

        return commandMutex.withLock {
            recentCommands[key] = now
            val packet = buildPacket(commandId, payload)
            val writeOk = deviceConnector.writeCharacteristic(
                ACTIONS_SERVICE_UUID,
                ACTIONS_COMMAND_CHARACTERISTIC_UUID,
                packet
            )
            if (!writeOk) return@withLock false

            val start = Clock.System.now().toEpochMilliseconds()
            val ack = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                ackPayloads.filter { it.cmdId == commandId }.first()
            }
            val elapsed = Clock.System.now().toEpochMilliseconds() - start
            val spacing = 20L
            if (elapsed < spacing) delay(spacing)
            return@withLock ack?.success ?: false
        }
    }

    /**
     * Handle incoming responses or notifications and emit to appropriate flows.
     *
     * Suspending so we can use `emit` (not `tryEmit`) on the shared flows. Combined with
     * BlueFalcon's per-characteristic ordered channel this guarantees we never silently
     * drop ACKs or notifications under bursty load — the previous tryEmit path would
     * return false and lose events when many feature subscribers were attached and a
     * single one momentarily lagged, surfacing as null/empty values on the dashboard.
     */
    private suspend fun handleResponseOrNotification(data: ByteArray) {
        if (data.size < 4) { // Min size: Vendor ID (2) + Command ID (2)
            logger.w { "Received invalid packet (too short): ${data.toHexString()}" }
            return
        }

        val vendorId = ((data[0].toUInt() and 0xFFu) shl 8) or (data[1].toUInt() and 0xFFu)
        val commandId = ((data[2].toUInt() and 0xFFu) shl 8) or (data[3].toUInt() and 0xFFu)

        // Check Vendor ID
        if (vendorId != TEUFEL_VENDOR_ID.toUInt()) {
            logger.w { "Received packet with invalid Vendor ID: 0x${vendorId.toString(16)}" }
            return
        }

        val cmdShort = commandId.toUShort()
        // The MYND firmware sends event notifications with the direction bit set on the
        // command id (0xc003 = EVENT_NOTIFICATION | ACK_BIT) — i.e. it uses the same
        // "set top bit when going device → host" convention as every other ACK
        // (0x08xx commands → 0x88xx ACK). The protocol spec example (Table 80) shows
        // the bare 0x4003 form, but the actual device on the wire matches what's in
        // Table 81 (0xc003 with the notificationId in the payload). Keep this check
        // BEFORE the generic ACK branch so notifications are not misclassified as
        // ACKs to a phantom 0x4003 command and silently dropped on the dashboard.
        val notificationCmdId: UShort = (ActionsCmd.EVENT_NOTIFICATION.id or ACK_BIT)
        if (cmdShort == notificationCmdId) {
            val payload = data.drop(4).toByteArray()
            if (payload.isEmpty()) {
                // Defensive: an empty 0xc003 packet would be a device-side ack-of-our-ack
                // (not observed on MYND firmware in practice, but ignore to avoid an
                // ack loop should a future firmware change the convention).
                return
            }
            val notificationId = payload[0]
            val dataPayload = payload.drop(1).toByteArray()
            logWithCount(
                "⚡ Notification: ID=0x${notificationId.toString(16)}, Payload=${dataPayload.toHexString()}",
                "notification_${notificationId}"
            )
            // Use the bare EVENT_NOTIFICATION id in the emitted payload so downstream
            // parseFromNotification helpers don't have to know about the direction bit.
            // The wire payload (notificationId byte at index 0) is preserved verbatim.
            _notificationPayloads.emit(NotificationPayload(ActionsCmd.EVENT_NOTIFICATION.id, payload))
            // Acknowledge the notification.
            // IMPORTANT (iOS): use write-WITHOUT-response. The auto-ACK is fire-and-forget;
            // using write-with-response queues the ACK in CoreBluetooth's serial
            // write-with-response queue alongside genuine user commands and starves them
            // when notifications arrive in bursts, which manifested as empty/stale dashboard
            // values (commands timing out waiting for ACKs that never returned in time).
            scope.launch {
                if (OtaSessionState.active.value) return@launch
                val ackData = byteArrayOf(notificationId) + dataPayload
                try {
                    val packet = buildPacket(notificationCmdId, ackData)
                    deviceConnector.writeCharacteristicWithoutResponse(
                        ACTIONS_SERVICE_UUID,
                        ACTIONS_COMMAND_CHARACTERISTIC_UUID,
                        packet
                    )
                } catch (_: Throwable) {
                    // best-effort ACK; ignore failures
                }
            }
            return
        }

        // ACK (top bit set) for a command we sent. Notifications (0xc003) are handled
        // above so this branch only fires for replies to user-issued commands.
        val isAck = (commandId and ACK_BIT.toUInt()) != 0u
        if (isAck) {
            val originalCommandId = (commandId.toInt() and ACK_BIT.inv().toInt()).toUShort()
            val payload = data.drop(4).toByteArray()
            val success = if (payload.isNotEmpty()) payload[0].toInt() == 0x00 else true
            val dataPayload = if (payload.size > 1) payload.drop(1).toByteArray() else byteArrayOf()

            // Log only once instead of three times
            logWithCount("✅ ACK: Cmd=0x${originalCommandId.toString(16)}, Success=${success}, Payload=${dataPayload.toHexString()}",
                          "ack_${originalCommandId}")

            // Suspending emit avoids silently dropping ACKs when many subscribers are
            // active (one per feature) — under bursty load tryEmit could return false
            // and a feature's value would never appear ("empty data" on the dashboard).
            _ackPayloads.emit(AckPayload(originalCommandId, success, dataPayload))
            return
        }

        logger.w { "Received unexpected packet type (CmdID: 0x${commandId.toString(16)}): ${data.toHexString()}" }
    }

    /**
     * Registers for a notification by sending the REGISTER_NOTIFICATION command
     */
    /**
     * ProtocolContext interface implementation
     */
    override suspend fun registerForNotification(notification: TeufelNotification): Boolean =
        ensureNotificationRegistered(notification.id.toByte())

    /**
     * Registers for a notification by sending the REGISTER_NOTIFICATION command
     */
    suspend fun ensureNotificationRegistered(notificationId: Byte): Boolean =
        notificationRegistrationMutex.withLock {
            if (OtaSessionState.active.value) return false
            if (registeredNotificationIds.contains(notificationId)) return true
            logger.d { "Registering notification id=0x${notificationId.toString(16)}" }
            // Use the command queue and await ACK to avoid Android GATT write races
            val ok = try {
                sendCommand(ActionsCmd.REGISTER_NOTIFICATION.id, byteArrayOf(notificationId))
            } catch (_: Throwable) { false }

            if (!ok) {
                logger.w { "Notification register failed for id=0x${notificationId.toString(16)}, retrying..." }
                delay(80)
                val retryOk = try { sendCommand(ActionsCmd.REGISTER_NOTIFICATION.id, byteArrayOf(notificationId)) } catch (_: Throwable) { false }
                if (retryOk) registeredNotificationIds.add(notificationId)
                return retryOk
            }
            registeredNotificationIds.add(notificationId)
            true
        }

    /**
     * Log with count limiter to reduce repeated log entries
     */
    private fun logWithCount(message: String, key: String) {
        val count = logCounters[key] ?: 0
        logCounters[key] = count + 1

        if (count == 0) logger.v { message }
    }

    private val featureFactories: Map<KClass<out Feature>, (Feature) -> Any> = mapOf(
        ActionsUpgrade::class to { _ -> ActionsUpgradeManager(deviceConnector) },
        AutoOffTimer::class to { definition -> ActionsAutoOffTimerFeature(this, definition as AutoOffTimer) },
        Battery::class to { definition -> ActionsBatteryFeature(this, definition as Battery) },
        BatteryCapacity::class to { definition -> ActionsBatteryCapacityFeature(this, definition as BatteryCapacity) },
        BatteryFriendlyCharging::class to { definition -> ActionsBatteryFriendlyChargingFeature(this, definition as BatteryFriendlyCharging) },
        ChargingStatus::class to { definition -> ActionsChargingStatusFeature(this, definition as ChargingStatus) },
        DeviceColor::class to { definition -> ActionsDeviceColorFeature(this, definition as DeviceColor) },
        EcoMode::class to { definition -> ActionsEcoModeFeature(this, definition as EcoMode) },
        EqGain::class to { definition -> ActionsEqGainFeature(this, definition as EqGain) },
        FirmwareVersion::class to { definition -> ActionsFirmwareVersionFeature(this, definition as FirmwareVersion) },
        LedBrightness::class to { definition -> ActionsLedBrightnessFeature(this, definition as LedBrightness) },
        MasterMute::class to { definition -> ActionsMasterMuteFeature(this, definition as MasterMute) },
        MasterVolume::class to { definition -> ActionsMasterVolumeFeature(this, definition as MasterVolume) },
        McuFirmwareVersion::class to { definition -> ActionsMcuFirmwareVersionFeature(this, definition as McuFirmwareVersion) },
        Multipoint::class to { definition -> ActionsMultipointFeature(this, definition as Multipoint) },
        PartyLinkBroadcast::class to { definition -> ActionsPartyLinkBroadcastFeature(this, definition as PartyLinkBroadcast) },
        SoundIcons::class to { definition -> ActionsSoundIconsFeature(this, definition as SoundIcons) },
        SourceSelection::class to { definition -> ActionsSourceSelectionFeature(this, definition as SourceSelection) },
    )

    override fun createFeature(definition: Feature): Any? =
        featureFactories[definition::class]?.invoke(definition)

    override fun supportedFeatureTypes(): Set<KClass<out Feature>> = featureFactories.keys
} 