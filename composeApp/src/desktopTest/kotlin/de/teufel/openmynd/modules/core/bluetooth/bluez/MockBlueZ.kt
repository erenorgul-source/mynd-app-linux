package de.teufel.openmynd.modules.core.bluetooth.bluez

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.ObjectManager
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt16
import org.freedesktop.dbus.types.Variant
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import org.bluez.Error as BlueZError

/** A throwaway `dbus-daemon` so tests never touch the real system bus. */
class PrivateDBusDaemon : AutoCloseable {
    private val socketDir = Files.createTempDirectory("openmynd-dbus").toFile()

    // An explicit path socket keeps the address independent of the distro's session.conf, which
    // may listen on an abstract socket that dbus-java's JDK unix socket transport can't use.
    private val process: Process = ProcessBuilder(
        "dbus-daemon", "--session", "--nofork", "--nopidfile", "--print-address",
        "--address=unix:path=${File(socketDir, "bus").absolutePath}",
    )
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()

    val address: String = process.inputStream.bufferedReader().readLine()
        ?: error("dbus-daemon did not print an address")

    fun connect(): DBusConnection = DBusConnectionBuilder.forAddress(address).withShared(false).build()

    override fun close() {
        process.destroy()
        process.waitFor(5, TimeUnit.SECONDS)
        socketDir.deleteRecursively()
    }

    companion object {
        val isAvailable: Boolean
            get() = System.getenv("PATH").orEmpty().split(File.pathSeparator)
                .any { File(it, "dbus-daemon").canExecute() }
    }
}

@DBusInterfaceName(BlueZ.GATT_SERVICE)
interface MockGattService1 : DBusInterface

/**
 * Just enough of bluetoothd's D-Bus API to drive [BlueZDeviceConnector]: an adapter, LE
 * devices that appear during discovery, and a GATT database resolved on connect.
 */
class MockBlueZ(private val daemon: PrivateDBusDaemon) : AutoCloseable {
    val connection: DBusConnection = daemon.connect().apply { requestBusName(BlueZ.BUS_NAME) }

    private val objects = linkedMapOf<String, MockObject>()

    /** Values written to characteristics: (characteristic path, data, write type). */
    val writes = CopyOnWriteArrayList<Triple<String, ByteArray, String>>()

    /** Called for each write to a characteristic; used to simulate device responses. */
    @Volatile
    var onWrite: (path: String, data: ByteArray) -> Unit = { _, _ -> }

    @Volatile
    var startDiscoveryError: (() -> Exception)? = null

    val adapter = MockAdapter("/org/bluez/hci0").also { add(it) }

    private val pendingDevices = mutableListOf<MockDevice>()

    init {
        connection.exportObject("/", object : ObjectManager {
            override fun GetManagedObjects(): Map<DBusPath, Map<String, Map<String, Variant<*>>>> =
                synchronized(objects) {
                    objects.values.associate { DBusPath(it.path) to mapOf(it.iface to it.properties.toMap()) }
                }

            override fun getObjectPath() = "/"
        })
    }

    /** A device that shows up (InterfacesAdded) once discovery starts. */
    fun addAdvertisingDevice(address: String, name: String, rssi: Short = -60): MockDevice {
        val device = MockDevice("${adapter.path}/dev_${address.replace(':', '_')}", address, name, rssi)
        synchronized(pendingDevices) { pendingDevices += device }
        return device
    }

    private fun add(obj: MockObject, announce: Boolean = false) {
        synchronized(objects) { objects[obj.path] = obj }
        connection.exportObject(obj.path, obj)
        if (announce) {
            connection.sendMessage(
                ObjectManager.InterfacesAdded("/", DBusPath(obj.path), mapOf(obj.iface to obj.properties.toMap()))
            )
        }
    }

    fun deviceByAddress(address: String): MockDevice =
        synchronized(objects) { objects.values.filterIsInstance<MockDevice>().first { it.properties["Address"]?.value == address } }

    fun characteristic(uuid: String): MockCharacteristic =
        synchronized(objects) { objects.values.filterIsInstance<MockCharacteristic>().first { it.uuid == uuid } }

    override fun close() {
        runCatching { connection.close() }
    }

    abstract inner class MockObject(private val objectPath: String, val iface: String) : Properties {
        val path: String get() = objectPath
        val properties = linkedMapOf<String, Variant<*>>()

        override fun getObjectPath() = objectPath

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
            properties.getValue(propertyName).value as A

        override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {
            val raw = if (value is Variant<*>) value.value else value
            update(propertyName, Variant(raw))
        }

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = properties.toMap()

        /** Updates a property and emits PropertiesChanged, like bluetoothd does. */
        fun update(name: String, value: Variant<*>) {
            properties[name] = value
            connection.sendMessage(Properties.PropertiesChanged(objectPath, iface, mapOf(name to value), emptyList()))
        }
    }

    inner class MockAdapter(path: String) : MockObject(path, BlueZ.ADAPTER), Adapter1 {
        var lastDiscoveryFilter: Map<String, Variant<*>> = emptyMap()

        init {
            properties["Address"] = Variant("00:11:22:33:44:55")
            properties["Powered"] = Variant(true)
            properties["Discovering"] = Variant(false)
        }

        override fun startDiscovery() {
            startDiscoveryError?.let { throw it() }
            update("Discovering", Variant(true))
            val devices = synchronized(pendingDevices) { pendingDevices.toList().also { pendingDevices.clear() } }
            devices.forEach { add(it, announce = true) }
        }

        override fun stopDiscovery() {
            if (properties["Discovering"]?.value != true) throw BlueZError.Failed("No discovery started")
            update("Discovering", Variant(false))
        }

        override fun setDiscoveryFilter(filter: Map<String, Variant<*>>) {
            lastDiscoveryFilter = filter
        }
    }

    inner class MockDevice(path: String, address: String, name: String, rssi: Short) :
        MockObject(path, BlueZ.DEVICE), Device1 {

        init {
            properties["Address"] = Variant(address)
            properties["Name"] = Variant(name)
            properties["Alias"] = Variant(name)
            properties["RSSI"] = Variant(rssi)
            properties["Connected"] = Variant(false)
            properties["ServicesResolved"] = Variant(false)
            properties["Paired"] = Variant(false)
        }

        override fun connect() {
            if (properties["Connected"]?.value == true) throw BlueZError.AlreadyConnected("Already Connected")
            update("Connected", Variant(true))
            // bluetoothd exports the GATT database, then flips ServicesResolved.
            Thread {
                Thread.sleep(50)
                val service = MockService("$path/service0001", ACTIONS_SERVICE)
                add(service, announce = true)
                add(MockCharacteristic("${service.path}/char0002", service.path, ACTIONS_COMMAND, listOf("write", "write-without-response")), announce = true)
                add(MockCharacteristic("${service.path}/char0004", service.path, ACTIONS_RESPONSE, listOf("notify")), announce = true)
                update("ServicesResolved", Variant(true))
            }.start()
        }

        override fun disconnect() = dropConnection()

        /** Simulates the link going away (device powered off, out of range, ...). */
        fun dropConnection() {
            update("ServicesResolved", Variant(false))
            update("Connected", Variant(false))
        }
    }

    inner class MockService(path: String, uuid: String) : MockObject(path, BlueZ.GATT_SERVICE), MockGattService1 {
        init {
            properties["UUID"] = Variant(uuid)
            properties["Primary"] = Variant(true)
        }
    }

    inner class MockCharacteristic(path: String, servicePath: String, val uuid: String, flags: List<String>) :
        MockObject(path, BlueZ.GATT_CHARACTERISTIC), GattCharacteristic1 {

        var readValue: ByteArray = byteArrayOf()

        init {
            properties["UUID"] = Variant(uuid)
            properties["Service"] = Variant(DBusPath(servicePath))
            properties["Flags"] = Variant(flags.toTypedArray())
            properties["Notifying"] = Variant(false)
            properties["MTU"] = Variant(UInt16(247))
        }

        override fun readValue(options: Map<String, Variant<*>>): ByteArray = readValue

        override fun writeValue(value: ByteArray, options: Map<String, Variant<*>>) {
            writes += Triple(path, value, options["type"]?.value as? String ?: "")
            onWrite(path, value)
        }

        override fun startNotify() = update("Notifying", Variant(true))

        override fun stopNotify() = update("Notifying", Variant(false))

        /** Sends a notification (a `Value` PropertiesChanged) if notifications are enabled. */
        fun notify(value: ByteArray) {
            check(properties["Notifying"]?.value == true) { "Notifications not enabled" }
            update("Value", Variant(value))
        }
    }

    companion object {
        const val ACTIONS_SERVICE = "00001100-d102-11e1-9b23-00025b00a5a5"
        const val ACTIONS_COMMAND = "00001101-d102-11e1-9b23-00025b00a5a5"
        const val ACTIONS_RESPONSE = "00001102-d102-11e1-9b23-00025b00a5a5"
    }
}
