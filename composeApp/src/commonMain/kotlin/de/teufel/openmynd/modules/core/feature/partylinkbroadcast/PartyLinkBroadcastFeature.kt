package de.teufel.openmynd.modules.core.feature.partylinkbroadcast

import de.teufel.openmynd.modules.core.feature.base.ToggleFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for PartyLink Broadcast feature.
 */
interface PartyLinkBroadcastFeature : ToggleFeature {
    /** Current broadcast active state. */
    val isActive: StateFlow<Boolean?>
    override val enabled: StateFlow<Boolean?> get() = isActive

    /** Start the PartyLink broadcast. */
    suspend fun start(): Boolean

    /** Stop the PartyLink broadcast. */
    suspend fun stop(): Boolean

    override suspend fun set(enabled: Boolean): Boolean =
        if (enabled) start() else stop()
} 