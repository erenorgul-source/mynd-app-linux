package de.teufel.openmynd.modules.core.feature.base

/**
 * Interface for features that require explicit cleanup.
 * 
 * Features implementing this interface will have their [onCleanup] method called
 * when the device disconnects or the session is cleared, allowing them to properly
 * cancel coroutines and release resources.
 */
interface FeatureLifecycle {
    /**
     * Called when the feature should clean up its resources.
     * Implementations should cancel coroutine scopes and release any held resources.
     */
    fun onCleanup()
}

