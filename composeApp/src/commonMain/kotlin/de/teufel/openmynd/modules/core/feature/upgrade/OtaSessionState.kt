package de.teufel.openmynd.modules.core.feature.upgrade

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared gate for pausing non-OTA protocol traffic while OTA is running.
 */
object OtaSessionState {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun setActive(value: Boolean) {
        _active.value = value
    }
}
