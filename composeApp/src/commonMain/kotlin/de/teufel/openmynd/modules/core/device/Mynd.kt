package de.teufel.openmynd.modules.core.device

import de.teufel.openmynd.modules.core.feature.*
import de.teufel.openmynd.modules.core.feature.EqBand.Bass
import de.teufel.openmynd.modules.core.feature.EqBand.Treble

data object Mynd : ActionsDevice {

    override val bluetoothNames = setOf("TMYND_BLE", "TMYND_BLE??")
    override val publicName = "MYND"

    override val features = super.features + setOf(
        ActionsUpgrade(needsChargerConnected = false),
        AutoOffTimer(),
        BatteryCapacity,
        BatteryFriendlyCharging,
        ChargingStatus,
        DeviceColor(),
        EcoMode,
        EqGain(
            bands = listOf(Bass, Treble),
            gainRange = -6..6,
            gainMultiplier = 10,
        ),
        LedBrightness(),
        MasterMute,
        MasterVolume(),
        McuFirmwareVersion(builtInDsp = false),
        Multipoint,
        PartyLinkBroadcast,
        SoundIcons(inverted = true),
        SourceSelection(),
    )
}