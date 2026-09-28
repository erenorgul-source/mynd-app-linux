package de.teufel.openmynd.modules.core.di

/**
 * BLE backends available on Android/iOS. The module wiring them lives in `mobileMain`;
 * the Linux desktop client always uses BlueZ over D-Bus.
 */
enum class ConnectorBackend { BLUE_FALCON, KABLE }

internal var selectedBackend: ConnectorBackend = ConnectorBackend.BLUE_FALCON
