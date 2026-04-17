package de.teufel.openmynd.modules.core.feature.soundicons

import de.teufel.openmynd.modules.core.feature.base.ToggleFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-specific interface for the sound icons feature.
 */
interface SoundIconsFeature : ToggleFeature {
    /** Current enabled state: true=on, false=off. */
    override val enabled: StateFlow<Boolean?>

    /** Enables sound icons on the device. */
    override suspend fun enable(): Boolean

    /** Disables sound icons on the device. */
    override suspend fun disable(): Boolean

    override suspend fun set(enabled: Boolean): Boolean =
        if (enabled) enable() else disable()
} 