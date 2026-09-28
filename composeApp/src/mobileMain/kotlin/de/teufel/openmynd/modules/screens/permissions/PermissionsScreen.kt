package de.teufel.openmynd.modules.screens.permissions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.compose.BindEffect
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import de.teufel.openmynd.utils.Platform
import de.teufel.openmynd.utils.getCurrentPlatform
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.permissions_required_title
import openmynd.composeapp.generated.resources.permissions_required_message
import openmynd.composeapp.generated.resources.current_states_format
import openmynd.composeapp.generated.resources.grant_permissions_button

@Composable
actual fun PermissionsRequired(
    onPermissionsGranted: @Composable () -> Unit,
    modifier: Modifier,
) {
    val permissionManager = koinInject<PermissionManager>()
    val permissionsController = koinInject<PermissionsController>()

    BindEffect(permissionsController)

    val coroutineScope = rememberCoroutineScope()
    val bluetoothScanPermissionState by permissionManager.bluetoothScanPermissionState.collectAsState()
    val bluetoothConnectPermissionState by permissionManager.bluetoothConnectPermissionState.collectAsState()

    LaunchedEffect(Unit) {
        if (getCurrentPlatform() == Platform.ANDROID) {
            permissionManager.refreshPermissionStates()
        }
    }

    if (getCurrentPlatform() != Platform.ANDROID) {
        Logger.withTag("Permissions").v { "Not Android, granting permissions directly." }
        onPermissionsGranted()
    } else {
        val allPermissionsGranted = bluetoothScanPermissionState == PermissionState.Granted &&
                                    bluetoothConnectPermissionState == PermissionState.Granted

        if (allPermissionsGranted) {
            onPermissionsGranted()
        } else {
            Box(
                modifier = modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(Res.string.permissions_required_title),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringResource(Res.string.permissions_required_message),
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = stringResource(
                            Res.string.current_states_format,
                            bluetoothScanPermissionState.toString(),
                            bluetoothConnectPermissionState.toString()
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                permissionManager.requestBluetoothScanPermission()
                                permissionManager.requestBluetoothConnectPermission()
                                permissionManager.refreshPermissionStates()
                            }
                        }
                    ) {
                        Text(stringResource(Res.string.grant_permissions_button))
                    }
                }
            }
        }
    }
} 