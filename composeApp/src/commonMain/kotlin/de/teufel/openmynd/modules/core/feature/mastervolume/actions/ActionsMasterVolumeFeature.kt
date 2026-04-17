package de.teufel.openmynd.modules.core.feature.mastervolume.actions

import de.teufel.openmynd.modules.core.feature.MasterVolume
import de.teufel.openmynd.modules.core.feature.base.FeatureLifecycle
import de.teufel.openmynd.modules.core.feature.mastervolume.MasterVolumeFeature
import de.teufel.openmynd.modules.core.feature.base.BaseRangeFeature
import de.teufel.openmynd.modules.core.feature.base.RangeCommandConfig
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.modules.core.protocol.actions.ActionsCmd
import de.teufel.openmynd.modules.core.protocol.actions.ActionsNotification
import de.teufel.openmynd.modules.core.protocol.actions.withActionsNotificationDefault
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * ACTIONS protocol implementation of the MasterVolumeFeature using BaseRangeFeature.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class ActionsMasterVolumeFeature(
    private val protocol: ActionsProtocol,
    definition: MasterVolume = MasterVolume()
) : MasterVolumeFeature, FeatureLifecycle {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val base = BaseRangeFeature(
        protocol = protocol,
        command = RangeCommandConfig(
            getCmd = ActionsCmd.GET_MASTER_VOLUME.id,
            setCmd = ActionsCmd.SET_MASTER_VOLUME.id,
            parseFromAck = { payload -> if (payload.isNotEmpty()) payload[0].toInt() and 0xFF else null },
            range = 0..100,
            parseFromNotification = { notif ->
                if (notif.payload.isNotEmpty() && notif.payload[0] == ActionsNotification.MASTER_VOLUME.id.toByte()) {
                    if (notif.payload.size >= 2) notif.payload[1].toInt() and 0xFF else null
                } else null
            }
        ),
        valueRetrieval = withActionsNotificationDefault(
            definition.valueRetrieval,
            ActionsNotification.MASTER_VOLUME
        )
    )

    override val volume: StateFlow<Int?> = base.value
    override val range: IntRange = base.range
    override suspend fun fetch(): Boolean = base.fetch()

    // Coalescing queue for rapid slider changes: only send the last value after brief debounce
    private val pendingSetValue = MutableStateFlow<Int?>(null)
    init {
        scope.launch {
            pendingSetValue
                .filterNotNull()
                .debounce(120)
                .distinctUntilChanged()
                .collect { latest ->
                    // fire-and-forget send; BaseRangeFeature will update optimistic state
                    base.set(latest)
                }
        }
    }

    override suspend fun set(value: Int): Boolean {
        val clamped = value.coerceIn(range)
        pendingSetValue.value = clamped
        // We optimistically return true; actual send occurs via coalesced flow
        return true
    }

    /**
     * Cancels the coroutine scope (debounce flow) and cleans up the base feature.
     * Called when the device disconnects or the session is cleared.
     */
    override fun onCleanup() {
        base.onCleanup()
        scope.cancel()
    }
}