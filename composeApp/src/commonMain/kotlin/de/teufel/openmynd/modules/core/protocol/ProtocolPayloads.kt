package de.teufel.openmynd.modules.core.protocol

/** Payload for ACK packets from the device. */
data class AckPayload(
    val cmdId: UShort,
    val success: Boolean,
    val payload: ByteArray,
)

/** Payload for notification packets from the device. */
data class NotificationPayload(
    val cmdId: UShort,
    val payload: ByteArray,
)
