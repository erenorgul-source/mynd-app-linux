package de.teufel.openmynd.modules.screens.knownDevices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.teufel.openmynd.modules.core.bluetooth.repository.StoredDevice
import de.teufel.openmynd.modules.screens.controlDevices.ControlDeviceScreen
import de.teufel.openmynd.modules.screens.searchDevices.ConnectionState
import de.teufel.openmynd.modules.ui.components.CommonEmptyView
import de.teufel.openmynd.modules.ui.components.CommonLoadingView
import de.teufel.openmynd.utils.collectAsStateWithLifecycle
import de.teufel.openmynd.modules.core.device.findDeviceTypeByName
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.clear_all_cd
import openmynd.composeapp.generated.resources.clear_all_devices
import openmynd.composeapp.generated.resources.known_devices_title
import openmynd.composeapp.generated.resources.known_devices_hint
import openmynd.composeapp.generated.resources.last_connected_format
import openmynd.composeapp.generated.resources.remove_device_cd
import openmynd.composeapp.generated.resources.connect
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Composable
fun KnownDevicesMainScreen(
    viewModel: KnownDevicesViewModel = koinInject()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navigator = LocalNavigator.currentOrThrow
    val rootNavigator = findRootNavigator(navigator)

    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            val device = (connectionState as ConnectionState.Connected).device
            rootNavigator.push(ControlDeviceScreen(deviceAddress = device.id))
        }
    }

    LaunchedEffect(state) {
        if (state is UiState.HasData) {
            val message = (state as UiState.HasData).message
            if (message != null) {
                snackbarHostState.showSnackbar(message)
                viewModel.clearMessage()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                state.isRefreshing -> {
                    CommonLoadingView(
                        modifier = Modifier.padding(top = 180.dp),
                    )
                }

                state is UiState.HasData -> {
                    val data = (state as UiState.HasData)
                    KnownDevicesList(
                        devices = data.devices,
                        onConnect = { viewModel.connectToDevice(it) },
                        onRemove = { viewModel.removeDevice(it.id) }
                    )

                    if (data.devices.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { viewModel.clearAllDevices() },
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(Res.string.clear_all_cd),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(stringResource(Res.string.clear_all_devices))
                        }
                    }
                }

                else -> {
                    CommonEmptyView(state)
                    Text(
                        stringResource(Res.string.known_devices_hint),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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

@Composable
private fun KnownDevicesList(
    devices: List<StoredDevice>,
    onConnect: (StoredDevice) -> Unit,
    onRemove: (StoredDevice) -> Unit
) {
    Text(
        stringResource(Res.string.known_devices_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(16.dp)
    )

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(devices.sortedByDescending { it.lastConnectedTimestamp }) { device ->
            DeviceItem(
                device = device,
                onConnect = { onConnect(device) },
                onRemove = { onRemove(device) }
            )
        }
    }
}

@Composable
private fun DeviceItem(
    device: StoredDevice,
    onConnect: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    val publicName = findDeviceTypeByName(device.name)?.publicName ?: device.name
                    Text(
                        text = publicName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(Res.string.last_connected_format, formatTimestamp(device.lastConnectedTimestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.remove_device_cd)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ElevatedButton(
                onClick = onConnect,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(Res.string.connect))
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.date} ${localDateTime.hour}:${
        localDateTime.minute.toString().padStart(2, '0')
    }"
}
