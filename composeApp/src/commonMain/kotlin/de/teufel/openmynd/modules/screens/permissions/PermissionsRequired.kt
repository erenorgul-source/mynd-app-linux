package de.teufel.openmynd.modules.screens.permissions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Gate shown before the main UI. Renders [onPermissionsGranted] once the platform is ready
 * to use Bluetooth: runtime permissions on Android/iOS, a powered BlueZ adapter on Linux.
 */
@Composable
expect fun PermissionsRequired(
    onPermissionsGranted: @Composable () -> Unit,
    modifier: Modifier = Modifier,
)
