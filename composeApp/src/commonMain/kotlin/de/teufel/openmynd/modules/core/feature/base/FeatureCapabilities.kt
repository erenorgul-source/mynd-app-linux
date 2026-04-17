package de.teufel.openmynd.modules.core.feature.base

import kotlinx.coroutines.flow.StateFlow

/**
 * Capability contract for read-only features that expose a single value.
 */
interface ReadFeature<T> {
    suspend fun fetch(): Boolean
    val value: StateFlow<T?>
}

/**
 * Capability contract for range-based numeric features.
 */
interface RangeFeature : ReadFeature<Int> {
    override val value: StateFlow<Int?>
    val range: IntRange
    suspend fun set(value: Int): Boolean
}

/**
 * Capability contract for boolean on/off style features.
 */
interface ToggleFeature : ReadFeature<Boolean> {
    val enabled: StateFlow<Boolean?>
    override val value: StateFlow<Boolean?> get() = enabled
    suspend fun set(enabled: Boolean): Boolean

    suspend fun enable(): Boolean = set(true)
    suspend fun disable(): Boolean = set(false)
}

/**
 * Capability contract for features selecting one value from a set of options.
 */
interface SelectorFeature<T> : ReadFeature<T> {
    val selected: StateFlow<T?>
    override val value: StateFlow<T?> get() = selected
    suspend fun select(value: T): Boolean
}
