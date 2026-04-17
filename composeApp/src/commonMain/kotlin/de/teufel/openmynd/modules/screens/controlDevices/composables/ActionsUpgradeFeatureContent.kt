package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.ActionsUpgrade
import de.teufel.openmynd.modules.core.feature.upgrade.ActionsFotaState
import de.teufel.openmynd.modules.core.feature.upgrade.ActionsUpgradeManager
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel

@Composable
fun ActionsUpgradeFeatureContent(viewModel: ControlDevicesViewModel) {
    val isSupported by viewModel.isFeatureSupported(ActionsUpgrade::class)
        .collectAsState(initial = false)

    if (!isSupported) return

    val manager = viewModel.getFeature<ActionsUpgradeManager>(ActionsUpgrade::class)
        ?: return

    val fotaState by manager.fotaState.collectAsState()

    val launchFilePicker = rememberFilePickerLauncher(
        onFilePicked = { name: String, data: ByteArray ->
            manager.selectFile(name, data)
        }
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Firmware Upgrade",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (val state = fotaState) {
                is ActionsFotaState.IDLE,
                is ActionsFotaState.UNKNOWN -> {
                    IdleContent(onChooseFile = { launchFilePicker() })
                }

                is ActionsFotaState.FILE_SELECTED -> {
                    FileSelectedContent(
                        fileName = state.fileName,
                        fileSize = state.fileSize,
                        onStartUpgrade = { manager.startUpgrade() },
                        onChooseDifferentFile = { launchFilePicker() },
                        onCancel = { manager.disconnectAndRelease() }
                    )
                }

                is ActionsFotaState.PREPARING,
                is ActionsFotaState.PREPARED -> {
                    PreparingContent()
                }

                is ActionsFotaState.TRANSFERRING -> {
                    TransferringContent(
                        progress = state.progress,
                        bytesTransferred = state.bytesTransferred,
                        totalBytes = state.totalBytes
                    )
                }

                is ActionsFotaState.TRANSFERRED -> {
                    TransferredContent(onDone = { manager.disconnectAndRelease() })
                }

                is ActionsFotaState.COMPLETED -> {
                    CompletedContent()
                }

                is ActionsFotaState.FAILED -> {
                    FailedContent(onReset = { manager.disconnectAndRelease() })
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Status: $fotaState",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IdleContent(onChooseFile: () -> Unit) {
    Text(
        text = "Select a firmware file to update your device.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onChooseFile,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Choose Firmware File")
    }
}

@Composable
private fun FileSelectedContent(
    fileName: String,
    fileSize: Int,
    onStartUpgrade: () -> Unit,
    onChooseDifferentFile: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Selected File",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "${fileSize / 1024} KB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onStartUpgrade,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Start Upgrade")
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = onChooseDifferentFile,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Choose Different File")
    }

    Spacer(modifier = Modifier.height(4.dp))

    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text("Cancel")
    }
}

@Composable
private fun PreparingContent() {
    Text(
        text = "Preparing firmware upgrade...",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(12.dp))
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
}

@Composable
private fun TransferringContent(progress: Int, bytesTransferred: Int, totalBytes: Int) {
    Text(
        text = "Transferring firmware...",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(12.dp))
    LinearProgressIndicator(
        progress = { progress / 100f },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "$progress%",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary
    )
    if (totalBytes > 0) {
        Text(
            text = "${bytesTransferred / 1024} / ${totalBytes / 1024} KB",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TransferredContent(onDone: () -> Unit) {
    Text(
        text = "Transfer complete!",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "The device is rebooting with the new firmware.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Done")
    }
}

@Composable
private fun CompletedContent() {
    Text(
        text = "Upgrade completed successfully!",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun FailedContent(onReset: () -> Unit) {
    Text(
        text = "Upgrade failed",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.error
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "An error occurred during the firmware upgrade. Please try again.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onReset,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Reset")
    }
}
