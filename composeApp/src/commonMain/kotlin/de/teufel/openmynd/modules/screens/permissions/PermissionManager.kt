package de.teufel.openmynd.modules.screens.permissions

import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.bluetooth.BLUETOOTH_CONNECT
import dev.icerock.moko.permissions.bluetooth.BLUETOOTH_SCAN
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionManager(
    private val permissionsController: PermissionsController
) {
    private val _bluetoothScanPermissionState = MutableStateFlow<PermissionState?>(null)
    val bluetoothScanPermissionState: StateFlow<PermissionState?> = _bluetoothScanPermissionState.asStateFlow()

    private val _bluetoothConnectPermissionState = MutableStateFlow<PermissionState?>(null)
    val bluetoothConnectPermissionState: StateFlow<PermissionState?> = _bluetoothConnectPermissionState.asStateFlow()


    suspend fun requestBluetoothScanPermission(): Boolean {
        return try {
            permissionsController.providePermission(Permission.BLUETOOTH_SCAN)
            _bluetoothScanPermissionState.value = permissionsController.getPermissionState(Permission.BLUETOOTH_SCAN)
            _bluetoothScanPermissionState.value == PermissionState.Granted
        } catch (_: Exception) {
            _bluetoothScanPermissionState.value = PermissionState.DeniedAlways
            false
        }
    }

    suspend fun requestBluetoothConnectPermission(): Boolean {
        return try {
            permissionsController.providePermission(Permission.BLUETOOTH_CONNECT)
            _bluetoothConnectPermissionState.value = permissionsController.getPermissionState(Permission.BLUETOOTH_CONNECT)
            _bluetoothConnectPermissionState.value == PermissionState.Granted
        } catch (_: Exception) {
            _bluetoothConnectPermissionState.value = PermissionState.DeniedAlways
            false
        }
    }

    /**
     * Refresh the current state of all permissions. This is a suspend function.
     */
    suspend fun refreshPermissionStates() {
        _bluetoothScanPermissionState.value = permissionsController.getPermissionState(Permission.BLUETOOTH_SCAN)
        _bluetoothConnectPermissionState.value = permissionsController.getPermissionState(Permission.BLUETOOTH_CONNECT)
    }
} 