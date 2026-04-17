package de.teufel.openmynd.modules.core.protocol.actions

import kotlin.jvm.JvmInline
import kotlin.ExperimentalUnsignedTypes

@OptIn(ExperimentalUnsignedTypes::class)
@JvmInline
value class ActionsCmd(val id: UShort) {
    companion object {
        val GET_BATTERY_LEVEL = ActionsCmd(0x0302u)
        val GET_POWER_ADAPTER_STATUS = ActionsCmd(0x0857u)
        val GET_AUTO_OFF_TIMER = ActionsCmd(0x0189u)
        val SET_AUTO_OFF_TIMER = ActionsCmd(0x0109u)

        val GET_DEVICE_COLOR = ActionsCmd(0x0900u)
        val ENABLE_ECO_MODE = ActionsCmd(0x0830u)
        val DISABLE_ECO_MODE = ActionsCmd(0x0831u)
        val GET_ECO_MODE_STATUS = ActionsCmd(0x0832u)
        val GET_EQ_PARAMETER_GAIN = ActionsCmd(0x090Du)
        val SET_EQ_PARAMETER_GAIN = ActionsCmd(0x090Fu)

        val ENABLE_VOICE_ASSISTANT = ActionsCmd(0x0805u)
        val DISABLE_VOICE_ASSISTANT = ActionsCmd(0x0806u)
        val GET_VOICE_ASSISTANT_STATUS = ActionsCmd(0x0807u)
        val GET_MASTER_VOLUME = ActionsCmd(0x0866u)
        val SET_MASTER_VOLUME = ActionsCmd(0x0867u)
        val GET_MASTER_MUTE_STATUS = ActionsCmd(0x0876u)
        val SET_MASTER_MUTE_STATUS = ActionsCmd(0x0877u)
        val GET_MCU_FIRMWARE_VERSION = ActionsCmd(0x0856u)
        val START_PARTYLINK_BROADCAST = ActionsCmd(0x0901u)
        val STOP_PARTYLINK_BROADCAST = ActionsCmd(0x0902u)
        val GET_PARTYLINK_BROADCAST_STATUS = ActionsCmd(0x0903u)
        val DISABLE_SOUND_ICONS = ActionsCmd(0x0828u)
        val ENABLE_SOUND_ICONS = ActionsCmd(0x0829u)
        val GET_SOUND_ICONS_STATUS = ActionsCmd(0x082Au)
        val GET_SOURCE = ActionsCmd(0x0880u)
        val SET_SOURCE = ActionsCmd(0x0881u)
        val GET_CONNECTED_SOURCES = ActionsCmd(0x0882u)
        val GET_BT_FIRMWARE_VERSION = ActionsCmd(0x0304u)
        val GET_BATTERY_CAPACITY = ActionsCmd(0x0913u)
        val ENABLE_BATTERY_FRIENDLY_CHARGING = ActionsCmd(0x0910u)
        val DISABLE_BATTERY_FRIENDLY_CHARGING = ActionsCmd(0x0911u)
        val GET_BATTERY_FRIENDLY_CHARGING_STATUS = ActionsCmd(0x0912u)
        val ENABLE_MULTIPOINT = ActionsCmd(0x084Cu)
        val DISABLE_MULTIPOINT = ActionsCmd(0x084Du)
        val GET_MULTIPOINT_STATUS = ActionsCmd(0x084Eu)
        val GET_LED_BRIGHTNESS = ActionsCmd(0x0840u)
        val SET_LED_BRIGHTNESS = ActionsCmd(0x0841u)
        val EVENT_NOTIFICATION = ActionsCmd(0x4003u)
        val REGISTER_NOTIFICATION = ActionsCmd(0x4001u)
        val CANCEL_NOTIFICATION = ActionsCmd(0x4002u)
    }
} 