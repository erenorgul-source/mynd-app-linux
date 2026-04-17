package de.teufel.openmynd.modules.core.feature.base

import co.touchlab.kermit.Logger
import de.teufel.openmynd.modules.core.protocol.AckPayload
import de.teufel.openmynd.modules.core.protocol.NotificationPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BaseFeatureLifecycleHelpersTest {

    @Test
    fun shouldListenToAcksMatchesRetrievalMode() {
        assertTrue(shouldListenToAcks(ValueRetrieval.FetchingOnly))
        assertTrue(
            shouldListenToAcks(
                ValueRetrieval.ViaNotification.NotificationsAndFetching(TeufelNotification(0x1234u)),
            ),
        )
        assertFalse(
            shouldListenToAcks(
                ValueRetrieval.ViaNotification.NotificationsOnly(TeufelNotification(0x1234u)),
            ),
        )
    }

    @Test
    fun scheduleInitialFetchRunsForFetchingModesOnly() = runTest {
        var fetchCalls = 0
        scheduleInitialFetch(
            valueRetrieval = ValueRetrieval.FetchingOnly,
            initialFetchDelayMs = 100,
            fetch = {
                fetchCalls++
                true
            },
        )

        runCurrent()
        assertEquals(0, fetchCalls)

        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, fetchCalls)
    }

    @Test
    fun scheduleInitialFetchSkipsNotificationOnlyMode() = runTest {
        var fetchCalls = 0
        scheduleInitialFetch(
            valueRetrieval = ValueRetrieval.ViaNotification.NotificationsOnly(TeufelNotification(0x1234u)),
            initialFetchDelayMs = 100,
            fetch = {
                fetchCalls++
                true
            },
        )

        advanceUntilIdle()
        assertEquals(0, fetchCalls)
    }

    @Test
    fun startRetryUntilFirstValueStopsAfterValueBecomesAvailable() = runTest {
        var fetchCalls = 0
        var hasValue = false

        startRetryUntilFirstValue(
            valueRetrieval = ValueRetrieval.FetchingOnly,
            retryConfig = RetryConfig(
                enabled = true,
                initialDelayMs = 10,
                maxDelayMs = 20,
                multiplier = 2.0,
            ),
            hasValue = { hasValue },
            fetch = {
                fetchCalls++
                if (fetchCalls >= 3) hasValue = true
                true
            },
        )

        advanceUntilIdle()
        assertTrue(hasValue)
        assertTrue(fetchCalls >= 3)
    }

    @Test
    fun setupNotificationUpdatesRegistersAndEmitsParsedValues() = runTest {
        val protocol = FakeProtocolContext()
        val notification = TeufelNotification(0x1234u)
        val observed = mutableListOf<Int>()

        setupNotificationUpdates(
            protocol = protocol,
            valueRetrieval = ValueRetrieval.ViaNotification.NotificationsOnly(notification),
            parseFromNotification = { payload -> payload.payload.firstOrNull()?.toInt() },
            onValue = { observed += it },
            logger = Logger.withTag("BaseFeatureLifecycleHelpersTest"),
        )

        advanceUntilIdle()
        assertEquals(listOf(notification), protocol.registeredNotifications)

        protocol.emitNotification(
            NotificationPayload(
                cmdId = notification.id,
                payload = byteArrayOf(7),
            ),
        )
        runCurrent()
        assertEquals(listOf(7), observed)
        coroutineContext.cancelChildren()
    }
}

private class FakeProtocolContext : ProtocolContext {
    private val notifications = MutableSharedFlow<NotificationPayload>(extraBufferCapacity = 4)
    val registeredNotifications = mutableListOf<TeufelNotification>()

    override suspend fun sendCommand(cmdId: UShort, payload: ByteArray): Boolean = true
    override val ackPayloads: Flow<AckPayload> = emptyFlow()
    override val notificationPayloads: Flow<NotificationPayload> = notifications

    override suspend fun registerForNotification(notification: TeufelNotification): Boolean {
        registeredNotifications += notification
        return true
    }

    fun emitNotification(payload: NotificationPayload) {
        notifications.tryEmit(payload)
    }
}
