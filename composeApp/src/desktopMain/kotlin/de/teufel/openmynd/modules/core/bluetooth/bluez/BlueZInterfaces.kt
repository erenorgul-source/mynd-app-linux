package de.teufel.openmynd.modules.core.bluetooth.bluez

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant

/**
 * Minimal Kotlin bindings for the parts of the BlueZ D-Bus API the app needs.
 * See https://github.com/bluez/bluez/tree/master/doc (org.bluez.*.rst).
 */
internal object BlueZ {
    const val BUS_NAME = "org.bluez"
    const val ROOT_PATH = "/org/bluez"

    const val ADAPTER = "org.bluez.Adapter1"
    const val DEVICE = "org.bluez.Device1"
    const val GATT_SERVICE = "org.bluez.GattService1"
    const val GATT_CHARACTERISTIC = "org.bluez.GattCharacteristic1"
}

@DBusInterfaceName(BlueZ.ADAPTER)
internal interface Adapter1 : DBusInterface {
    @DBusMemberName("StartDiscovery")
    fun startDiscovery()

    @DBusMemberName("StopDiscovery")
    fun stopDiscovery()

    @DBusMemberName("SetDiscoveryFilter")
    fun setDiscoveryFilter(filter: Map<String, Variant<*>>)
}

@DBusInterfaceName(BlueZ.DEVICE)
internal interface Device1 : DBusInterface {
    @DBusMemberName("Connect")
    fun connect()

    @DBusMemberName("Disconnect")
    fun disconnect()
}

@DBusInterfaceName(BlueZ.GATT_CHARACTERISTIC)
internal interface GattCharacteristic1 : DBusInterface {
    @DBusMemberName("ReadValue")
    fun readValue(options: Map<String, Variant<*>>): ByteArray

    @DBusMemberName("WriteValue")
    fun writeValue(value: ByteArray, options: Map<String, Variant<*>>)

    @DBusMemberName("StartNotify")
    fun startNotify()

    @DBusMemberName("StopNotify")
    fun stopNotify()
}
