package de.teufel.openmynd.modules.core.bluetooth.bluez

import de.teufel.openmynd.modules.core.bluetooth.bluez.MockBlueZ.Companion.ACTIONS_COMMAND
import de.teufel.openmynd.modules.core.bluetooth.bluez.MockBlueZ.Companion.ACTIONS_RESPONSE
import de.teufel.openmynd.modules.core.bluetooth.bluez.MockBlueZ.Companion.ACTIONS_SERVICE
import de.teufel.openmynd.modules.core.bluetooth.model.BluetoothDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.freedesktop.dbus.types.Variant
import org.junit.Assume.assumeTrue
import org.bluez.Error as BlueZError
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs [BlueZDeviceConnector] against [MockBlueZ] on a private dbus-daemon, so the real D-Bus
 * marshalling, signal delivery and error mapping are exercised. Skipped without dbus-daemon.
 */
class BlueZDeviceConnectorTest {
    private lateinit var daemon: PrivateDBusDaemon
    private lateinit var mock: MockBlueZ
    private lateinit var client: BlueZClient
    private lateinit var connector: BlueZDeviceConnector

    @BeforeTest
    fun setUp() {
        assumeTrue("dbus-daemon not installed", PrivateDBusDaemon.isAvailable)
        daemon = PrivateDBusDaemon()
        mock = MockBlueZ(daemon)
        client = BlueZClient { daemon.connect() }
        connector = BlueZDeviceConnector(client)
    }

    @AfterTest
    fun tearDown() {
        // Close whatever setUp managed to create, even if it failed halfway.
        if (::client.isInitialized) client.close()
        if (::mock.isInitialized) mock.close()
        if (::daemon.isInitialized) daemon.close()
    }

    private fun test(block: suspend CoroutineScope.() -> Unit) = runBlocking { withTimeout(15_000) { block() } }

    private suspend fun startClient(): BlueZState.Ready {
        client.start()
        return client.state.filterIsInstance<BlueZState.Ready>().first()
    }

    private suspend fun discoverMynd(): BluetoothDevice {
        mock.addAdvertisingDevice("AA:BB:CC:DD:EE:01", "TMYND_BLE", rssi = -50)
        startClient()
        connector.startScan()
        return connector.discoveredDevices.first { it.isNotEmpty() }.single()
    }

    private suspend fun connectMynd(): BluetoothDevice {
        val device = discoverMynd()
        assertTrue(connector.connect(device))
        connector.isReady.first { it }
        return device
    }

    @Test
    fun reportsAdapterStateAndPowersOn() = test {
        mock.adapter.properties["Powered"] = Variant(false)
        client.start()
        assertEquals(BlueZState.PoweredOff("/org/bluez/hci0"), client.state.first { it !is BlueZState.Initializing })

        assertTrue(client.setAdapterPowered(true).isSuccess)
        assertEquals(BlueZState.Ready("/org/bluez/hci0"), client.state.first { it is BlueZState.Ready })
    }

    @Test
    fun unavailableWithoutBluetoothd() = test {
        mock.connection.releaseBusName(BlueZ.BUS_NAME)
        client.start()
        assertTrue(client.state.first { it !is BlueZState.Initializing } is BlueZState.Unavailable)
    }

    @Test
    fun scanListsOnlySupportedLeDevices() = test {
        mock.addAdvertisingDevice("AA:BB:CC:DD:EE:02", "Some Headphones", rssi = -40)
        val device = discoverMynd()

        assertEquals("AA:BB:CC:DD:EE:01", device.id)
        assertEquals("TMYND_BLE", device.name)
        assertEquals(-50, device.rssi)
        assertTrue(connector.isScanning.value)
        assertEquals("le", mock.adapter.lastDiscoveryFilter["Transport"]?.value)

        connector.stopScan()
        connector.isScanning.first { !it }
        eventually { mock.adapter.properties["Discovering"]?.value == false }
    }

    @Test
    fun scanToleratesDiscoveryAlreadyInProgress() = test {
        mock.startDiscoveryError = { BlueZError.InProgress("Operation already in progress") }
        startClient()
        connector.startScan()
        delay(300)
        assertTrue(connector.isScanning.value, "an InProgress error must not abort the scan")
    }

    @Test
    fun connectResolvesGattAndBecomesReady() = test {
        val device = connectMynd()

        assertEquals(device.id, connector.connectedDevice.value?.id)
        assertFalse(connector.isScanning.value, "scan is stopped before connecting")
        assertTrue(connector.requestMtu(512))
    }

    @Test
    fun connectToleratesAlreadyConnected() = test {
        val device = discoverMynd()
        // Connected from elsewhere (e.g. the desktop's Bluetooth settings) before we try.
        client.device("/org/bluez/hci0/dev_AA_BB_CC_DD_EE_01").connect()
        assertTrue(connector.connect(device))
        connector.isReady.first { it }
    }

    @Test
    fun writesUseRequestOrCommandType() = test {
        connectMynd()
        assertTrue(connector.writeCharacteristic(ACTIONS_SERVICE, ACTIONS_COMMAND, byteArrayOf(1, 2)))
        assertTrue(connector.writeCharacteristicWithoutResponse(ACTIONS_SERVICE, ACTIONS_COMMAND, byteArrayOf(3)))

        assertEquals(listOf("request", "command"), mock.writes.map { it.third })
        assertContentEquals(byteArrayOf(1, 2), mock.writes[0].second)
        assertFalse(connector.writeCharacteristic(ACTIONS_SERVICE, "0000ffff-0000-1000-8000-00805f9b34fb", byteArrayOf(0)))
    }

    @Test
    fun notificationsArriveInOrderAfterSubscribe() = test {
        connectMynd()
        val response = mock.characteristic(ACTIONS_RESPONSE)
        mock.onWrite = { _, data -> response.notify(data + 0x7f) }

        val updates = connector.subscribeToCharacteristic(ACTIONS_SERVICE, ACTIONS_RESPONSE)
        assertEquals(true, response.properties["Notifying"]?.value, "StartNotify returned before CCCD was enabled")

        val received = async { updates.take(51).toList() }
        delay(100) // let the collector attach (SharedFlow has no replay)
        // A write whose "ACK" is a notification, then a burst of fragments.
        connector.writeCharacteristic(ACTIONS_SERVICE, ACTIONS_COMMAND, byteArrayOf(0x10))
        repeat(50) { response.notify(byteArrayOf(it.toByte())) }

        val values = received.await()
        assertContentEquals(byteArrayOf(0x10, 0x7f), values.first())
        assertEquals((0 until 50).toList(), values.drop(1).map { it.single().toInt() })
    }

    @Test
    fun readReturnsCharacteristicValue() = test {
        connectMynd()
        mock.characteristic(ACTIONS_COMMAND).readValue = byteArrayOf(9, 8, 7)
        assertContentEquals(byteArrayOf(9, 8, 7), connector.readCharacteristic(ACTIONS_SERVICE, ACTIONS_COMMAND).first())
    }

    @Test
    fun remoteDisconnectClearsState() = test {
        val device = connectMynd()
        mock.deviceByAddress(device.id).dropConnection()

        connector.connectedDevice.first { it == null }
        assertFalse(connector.isReady.value)
        assertFalse(connector.writeCharacteristic(ACTIONS_SERVICE, ACTIONS_COMMAND, byteArrayOf(1)))
    }

    @Test
    fun disconnect() = test {
        connectMynd()
        assertTrue(connector.disconnect())
        assertNull(connector.connectedDevice.value)
        assertFalse(connector.isReady.value)
    }
}

/** Polls [condition] until it holds; for state that lives in the mock, not in a flow. */
private suspend fun eventually(condition: () -> Boolean) {
    while (!condition()) delay(20)
}
