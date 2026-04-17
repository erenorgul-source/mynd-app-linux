package de.teufel.openmynd.modules.core.feature.base

import co.touchlab.kermit.Logger
import de.teufel.openmynd.modules.core.protocol.NotificationPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal fun shouldListenToAcks(valueRetrieval: ValueRetrieval): Boolean =
    valueRetrieval is ValueRetrieval.FetchingOnly ||
        valueRetrieval is ValueRetrieval.ViaNotification.NotificationsAndFetching

internal fun <T : Any> CoroutineScope.setupNotificationUpdates(
    protocol: ProtocolContext,
    valueRetrieval: ValueRetrieval,
    parseFromNotification: (NotificationPayload) -> T?,
    onValue: (T) -> Unit,
    logger: Logger,
) {
    if (valueRetrieval !is ValueRetrieval.ViaNotification) return
    val notification = valueRetrieval.notification

    protocol.notificationPayloads
        .mapNotNull(parseFromNotification)
        .onEach(onValue)
        .launchIn(this)

    launch {
        try {
            val success = protocol.registerForNotification(notification)
            if (!success) {
                logger.w { "Failed to register for notification: ${notification.id}" }
            }
        } catch (e: Throwable) {
            logger.e(e) { "Error registering for notification: ${notification.id}" }
        }
    }
}

internal fun CoroutineScope.scheduleInitialFetch(
    valueRetrieval: ValueRetrieval,
    initialFetchDelayMs: Long,
    fetch: suspend () -> Boolean,
) {
    val shouldFetchInitially = valueRetrieval is ValueRetrieval.FetchingOnly ||
        valueRetrieval is ValueRetrieval.ViaNotification.NotificationsAndFetching

    if (!shouldFetchInitially) return

    launch {
        delay(initialFetchDelayMs)
        fetch()
    }
}

internal fun CoroutineScope.startRetryUntilFirstValue(
    valueRetrieval: ValueRetrieval,
    retryConfig: RetryConfig,
    hasValue: () -> Boolean,
    fetch: suspend () -> Boolean,
) {
    if (!retryConfig.enabled || valueRetrieval is ValueRetrieval.ViaNotification.NotificationsOnly) return

    launch {
        var delayMs = retryConfig.initialDelayMs
        while (!hasValue()) {
            delay(delayMs)
            try {
                fetch()
            } catch (_: Throwable) {
            }
            delayMs = kotlin.math.min(
                (delayMs * retryConfig.multiplier).toLong(),
                retryConfig.maxDelayMs,
            )
        }
    }
}
