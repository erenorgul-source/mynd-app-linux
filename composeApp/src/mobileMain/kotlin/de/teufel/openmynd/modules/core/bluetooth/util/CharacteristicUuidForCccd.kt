package de.teufel.openmynd.modules.core.bluetooth.util

import dev.bluefalcon.BluetoothCharacteristicDescriptor

/**
 * When a GATT descriptor write completes, returns the parent characteristic UUID (lowercase
 * string form) if this was a CCCD (notify/indicate subscription) write; otherwise null.
 *
 * BlueFalcon maps iOS descriptor completion to [dev.bluefalcon.BlueFalconDelegate.didWriteCharacteristic]
 * with the parent characteristic; Android uses [dev.bluefalcon.BlueFalconDelegate.didWriteDescriptor]
 * instead, so the connector completes CCCD waiters in both places.
 */
internal expect fun characteristicUuidIfCccdDescriptorWrite(
    descriptor: BluetoothCharacteristicDescriptor
): String?
