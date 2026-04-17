package de.teufel.openmynd.modules.core.feature.base

/**
 * Describes how a feature's value is retrieved/updated. Intended to be supplied
 * via the device definition so feature wiring knows whether to subscribe to
 * notifications, perform fetches, or both.
 */
sealed interface ValueRetrieval {

    /** Feature value can only be fetched when needed; there are no notifications. */
    object FetchingOnly : ValueRetrieval

    /** Feature emits notifications (optionally combined with fetching). */
    sealed interface ViaNotification : ValueRetrieval {
        val notification: TeufelNotification

        /** Only notifications update the value (no fetch). */
        data class NotificationsOnly(override val notification: TeufelNotification) : ViaNotification

        /** Both notifications and fetches can be used to update the value. */
        data class NotificationsAndFetching(override val notification: TeufelNotification) : ViaNotification
    }
}


