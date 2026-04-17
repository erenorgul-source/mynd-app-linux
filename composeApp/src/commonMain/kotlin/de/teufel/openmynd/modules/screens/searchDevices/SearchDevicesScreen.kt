package de.teufel.openmynd.modules.screens.searchDevices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.utils.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.teufel.openmynd.modules.core.bluetooth.model.BluetoothDevice
import cafe.adriel.voyager.navigator.Navigator
import co.touchlab.kermit.Logger
import de.teufel.openmynd.modules.screens.controlDevices.ControlDeviceScreen
import de.teufel.openmynd.modules.core.device.findDeviceTypeByName
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.scan_for_devices_title
import openmynd.composeapp.generated.resources.refresh_scan_cd
import openmynd.composeapp.generated.resources.scanning
import openmynd.composeapp.generated.resources.start_scan
import openmynd.composeapp.generated.resources.searching_devices
import openmynd.composeapp.generated.resources.no_teufel_devices_found
import openmynd.composeapp.generated.resources.device_id_format
import openmynd.composeapp.generated.resources.rssi_dbm_format
import openmynd.composeapp.generated.resources.connected_to_format
import openmynd.composeapp.generated.resources.disconnect
import openmynd.composeapp.generated.resources.connecting_to_format
import openmynd.composeapp.generated.resources.disconnected
import openmynd.composeapp.generated.resources.connection_failed_format
import openmynd.composeapp.generated.resources.not_connected
import openmynd.composeapp.generated.resources.ellipsis
import openmynd.composeapp.generated.resources.unknown
import openmynd.composeapp.generated.resources.error_prefix

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchDevicesMainContent(
    viewModel: SearchDevicesViewModel = koinInject()
) {
    val tabNavigator = LocalNavigator.currentOrThrow
    val rootNavigator = findRootNavigator(tabNavigator)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        Logger.withTag("SearchDevicesScreen").v { "LaunchedEffect - Requesting initial scan" }
        viewModel.performInitialScan()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            scope.launch { snackbarHostState.showSnackbar("Error: $it") }
        }
    }

    LaunchedEffect(uiState.connectionState) {
        if (uiState.connectionState is ConnectionState.Connected) {
            val device = (uiState.connectionState as ConnectionState.Connected).device
            Logger.withTag("SearchDevicesScreen").d { "Navigating to ControlDeviceScreen for ${device.id}" }
            rootNavigator.push(ControlDeviceScreen(deviceAddress = device.id))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.scan_for_devices_title)) },
                actions = {
                    IconButton(onClick = { viewModel.startScan() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.refresh_scan_cd))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { viewModel.startScan() },
                modifier = Modifier.fillMaxWidth()
            ) {
                val primaryLabelRes = if (uiState.isLoading) Res.string.scanning else Res.string.start_scan
                Text(stringResource(primaryLabelRes))
            }
            Spacer(Modifier.height(16.dp))

            if (uiState.isLoading && uiState.devices.isEmpty()) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.searching_devices))
            }

            if (!uiState.isLoading && uiState.devices.isNotEmpty()) {
                DeviceList(devices = uiState.devices) { device ->
                    viewModel.connectToDevice(device)
                }
            } else if (!uiState.isLoading && uiState.devices.isEmpty()) {
                Text(stringResource(Res.string.no_teufel_devices_found))
            }

            Spacer(Modifier.height(16.dp))
            ConnectionStatusFooter(uiState.connectionState) {
                Logger.withTag("SearchDevicesScreen").v { "Disconnect button clicked" }
                viewModel.disconnectDevice()
            }

            uiState.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.error_prefix) + it, color = Color.Red)
            }

            Spacer(Modifier.weight(1f))
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

@Composable
private fun DeviceList(devices: List<BluetoothDevice>, onDeviceClick: (BluetoothDevice) -> Unit) {
    LazyColumn {
        items(devices, key = { it.id }) { device ->
            DeviceItem(device = device, onClick = { onDeviceClick(device) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun DeviceItem(device: BluetoothDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val publicName = findDeviceTypeByName(device.name)?.publicName ?: device.name
            Text(text = publicName)
            Text(text = stringResource(Res.string.device_id_format, device.id), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(16.dp))
        Text(text = stringResource(Res.string.rssi_dbm_format, device.rssi))
    }
}

@Composable
private fun ConnectionStatusFooter(connectionState: ConnectionState, onDisconnectClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        when (connectionState) {
            is ConnectionState.Connected -> {
                Column {
                    val publicName = findDeviceTypeByName(connectionState.device.name)?.publicName ?: connectionState.device.name
                    Text(stringResource(Res.string.connected_to_format, publicName), style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onDisconnectClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(Res.string.disconnect))
                }
            }
            is ConnectionState.Connecting -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.connecting_to_format, connectionState.address ?: stringResource(Res.string.ellipsis)), style = MaterialTheme.typography.bodySmall)
                }
            }
            is ConnectionState.Disconnected -> {
                Text(stringResource(Res.string.disconnected), style = MaterialTheme.typography.bodySmall)
            }
            is ConnectionState.Failed -> {
                Text(stringResource(Res.string.connection_failed_format, connectionState.error ?: stringResource(Res.string.unknown)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            is ConnectionState.Idle -> {
                Text(stringResource(Res.string.not_connected), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun findRootNavigator(navigator: Navigator): Navigator {
    var currentNavigator: Navigator? = navigator
    while (currentNavigator?.parent != null) {
        currentNavigator = currentNavigator.parent
    }
    return currentNavigator ?: navigator
}
