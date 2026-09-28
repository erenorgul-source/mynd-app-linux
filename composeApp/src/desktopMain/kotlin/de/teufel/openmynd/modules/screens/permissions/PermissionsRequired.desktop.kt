package de.teufel.openmynd.modules.screens.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.bluetooth.bluez.BlueZClient
import de.teufel.openmynd.modules.core.bluetooth.bluez.BlueZState
import kotlinx.coroutines.launch
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.bluetooth_blocked_message
import openmynd.composeapp.generated.resources.bluetooth_blocked_title
import openmynd.composeapp.generated.resources.bluetooth_checking
import openmynd.composeapp.generated.resources.bluetooth_no_adapter_message
import openmynd.composeapp.generated.resources.bluetooth_no_adapter_title
import openmynd.composeapp.generated.resources.bluetooth_off_banner
import openmynd.composeapp.generated.resources.bluetooth_off_message
import openmynd.composeapp.generated.resources.bluetooth_off_title
import openmynd.composeapp.generated.resources.bluetooth_turn_on
import openmynd.composeapp.generated.resources.bluetooth_turn_on_failed
import openmynd.composeapp.generated.resources.bluetooth_unavailable_message
import openmynd.composeapp.generated.resources.bluetooth_unavailable_title
import openmynd.composeapp.generated.resources.retry
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val START_BLUETOOTH_COMMAND = "sudo systemctl enable --now bluetooth.service"
private const val UNBLOCK_BLUETOOTH_COMMAND = "rfkill unblock bluetooth"

/**
 * Linux has no runtime Bluetooth permissions; instead make sure BlueZ is running and the
 * adapter is powered. Once the app got going, losing Bluetooth only shows a banner so the
 * navigation state survives e.g. toggling Bluetooth in the system tray.
 */
@Composable
actual fun PermissionsRequired(
    onPermissionsGranted: @Composable () -> Unit,
    modifier: Modifier,
) {
    val bluez = koinInject<BlueZClient>()
    val state by bluez.state.collectAsState()
    var wasReady by remember { mutableStateOf(false) }
    if (state is BlueZState.Ready) wasReady = true

    if (!wasReady) {
        BluetoothStatus(state, bluez, modifier.fillMaxSize())
        return
    }
    Column(modifier.fillMaxSize()) {
        if (state !is BlueZState.Ready) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.bluetooth_off_banner),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        Box(Modifier.weight(1f)) {
            onPermissionsGranted()
        }
    }
}

@Composable
private fun BluetoothStatus(state: BlueZState, bluez: BlueZClient, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var powerOnFailed by remember { mutableStateOf(false) }

    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                BlueZState.Initializing, is BlueZState.Ready -> {
                    CircularProgressIndicator()
                    Text(stringResource(Res.string.bluetooth_checking), textAlign = TextAlign.Center)
                }

                is BlueZState.Unavailable -> {
                    StatusText(Res.string.bluetooth_unavailable_title, Res.string.bluetooth_unavailable_message)
                    Command(START_BLUETOOTH_COMMAND)
                    OutlinedButton(onClick = { bluez.start() }) { Text(stringResource(Res.string.retry)) }
                }

                BlueZState.NoAdapter -> {
                    StatusText(Res.string.bluetooth_no_adapter_title, Res.string.bluetooth_no_adapter_message)
                    OutlinedButton(onClick = { bluez.start() }) { Text(stringResource(Res.string.retry)) }
                }

                is BlueZState.Blocked -> {
                    StatusText(Res.string.bluetooth_blocked_title, Res.string.bluetooth_blocked_message)
                    Command(UNBLOCK_BLUETOOTH_COMMAND)
                }

                is BlueZState.PoweredOff -> {
                    StatusText(Res.string.bluetooth_off_title, Res.string.bluetooth_off_message)
                    Button(onClick = {
                        scope.launch { powerOnFailed = bluez.setAdapterPowered(true).isFailure }
                    }) {
                        Text(stringResource(Res.string.bluetooth_turn_on))
                    }
                    if (powerOnFailed) {
                        Text(
                            text = stringResource(Res.string.bluetooth_turn_on_failed),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusText(title: StringResource, message: StringResource) {
    Text(stringResource(title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(message), textAlign = TextAlign.Center)
}

/** A shell command the user can select and copy. */
@Composable
private fun Command(command: String) {
    SelectionContainer {
        Text(
            text = command,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
