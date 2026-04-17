package de.teufel.openmynd.modules.core.protocol.actions

import kotlin.jvm.JvmInline

/**
 * Wraps ACTIONS notification identifiers.
 */
@OptIn(ExperimentalUnsignedTypes::class)
@JvmInline
value class ActionsNotification(val id: UShort) {
    companion object {
        val POWER_ADAPTER_STATUS = ActionsNotification(0x09u)
        val MASTER_VOLUME = ActionsNotification(0x85u)
        val MASTER_MUTE_STATUS = ActionsNotification(0x86u)
        val ECO_MODE_STATUS = ActionsNotification(0x89u)
        val BATTERY_LEVEL = ActionsNotification(0x8au)
        val SOUND_ICONS_STATUS = ActionsNotification(0x8eu)
        val PARTY_LINK_STATUS = ActionsNotification(0x92u)
        val CONNECTED_SOURCES = ActionsNotification(0x97u)
        val BATTERY_FRIENDLY_CHARGING_STATUS = ActionsNotification(0x98u)
    }
}