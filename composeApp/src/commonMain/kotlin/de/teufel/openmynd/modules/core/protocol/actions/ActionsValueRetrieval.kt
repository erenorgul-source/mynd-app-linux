package de.teufel.openmynd.modules.core.protocol.actions

import de.teufel.openmynd.modules.core.feature.base.TeufelNotification
import de.teufel.openmynd.modules.core.feature.base.ValueRetrieval
import de.teufel.openmynd.modules.core.feature.base.ValueRetrieval.ViaNotification.NotificationsAndFetching

/**
 * Uses protocol-local notification IDs as defaults when a feature definition
 * does not explicitly request notification-based retrieval.
 */
internal fun withActionsNotificationDefault(
    current: ValueRetrieval,
    notification: ActionsNotification
): ValueRetrieval =
    when (current) {
        is ValueRetrieval.FetchingOnly -> NotificationsAndFetching(TeufelNotification(notification.id))
        else -> current
    }
