package de.teufel.openmynd.modules.core.bluetooth.util

import android.bluetooth.BluetoothGattDescriptor
import dev.bluefalcon.BluetoothCharacteristicDescriptor

private val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb".lowercase()

internal actual fun characteristicUuidIfCccdDescriptorWrite(
    descriptor: BluetoothCharacteristicDescriptor
): String? {
    val d = descriptor as BluetoothGattDescriptor
    if (d.uuid.toString().lowercase() != CCCD_UUID) return null
    return d.characteristic?.uuid?.toString()?.lowercase()
}
