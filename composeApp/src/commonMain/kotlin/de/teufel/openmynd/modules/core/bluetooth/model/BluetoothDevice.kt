package de.teufel.openmynd.modules.core.bluetooth.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Library-agnostic representation of a Bluetooth device.
 */
@OptIn(ExperimentalTime::class)
data class BluetoothDevice  constructor(
    val id: String,
    val name: String,
    val rssi: Int,
    val isConnectable: Boolean,
    val lastSeen: Long = Clock.System.now().toEpochMilliseconds()
)