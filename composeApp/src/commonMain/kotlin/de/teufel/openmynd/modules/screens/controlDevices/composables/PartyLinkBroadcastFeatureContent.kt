package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.PartyLinkBroadcast
import de.teufel.openmynd.modules.core.feature.partylinkbroadcast.PartyLinkBroadcastFeature
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import de.teufel.openmynd.modules.screens.controlDevices.FeatureContent
import de.teufel.openmynd.modules.screens.controlDevices.composables.common.FeatureSectionHeader
import de.teufel.openmynd.modules.screens.controlDevices.composables.common.ToggleRow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.partylink_broadcast_title
import openmynd.composeapp.generated.resources.partylink_broadcast_label
import openmynd.composeapp.generated.resources.partylink_broadcast_desc
import openmynd.composeapp.generated.resources.starting_partylink_broadcast
import openmynd.composeapp.generated.resources.stopping_partylink_broadcast
import openmynd.composeapp.generated.resources.partylink_broadcast_started
import openmynd.composeapp.generated.resources.partylink_broadcast_stopped
import openmynd.composeapp.generated.resources.partylink_broadcast_failed_start
import openmynd.composeapp.generated.resources.partylink_broadcast_failed_stop

/**
 * A composable that shows PartyLink Broadcast toggle, displayed when supported.
 */
@Composable
fun PartyLinkBroadcastFeatureContent(viewModel: ControlDevicesViewModel) {
    FeatureContent<PartyLinkBroadcastFeature>(
        viewModel = viewModel,
        featureClass = PartyLinkBroadcast::class,
        dataReady = { feature ->
            feature.isActive.collectAsState(initial = null).value != null
        }
    ) { feature ->
        PartyLinkBroadcastFeatureContentImpl(feature) { message ->
            viewModel.showMessage(message)
        }
    }
}

@Composable
private fun PartyLinkBroadcastFeatureContentImpl(
    feature: PartyLinkBroadcastFeature,
    showMessage: (String) -> Unit
) {
    val isActive by feature.isActive.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        val startingText = stringResource(Res.string.starting_partylink_broadcast)
        val stoppingText = stringResource(Res.string.stopping_partylink_broadcast)
        val startedText = stringResource(Res.string.partylink_broadcast_started)
        val stoppedText = stringResource(Res.string.partylink_broadcast_stopped)
        val failedStartText = stringResource(Res.string.partylink_broadcast_failed_start)
        val failedStopText = stringResource(Res.string.partylink_broadcast_failed_stop)
        FeatureSectionHeader(title = stringResource(Res.string.partylink_broadcast_title))

        Spacer(modifier = Modifier.height(4.dp))

        ToggleRow(
            label = stringResource(Res.string.partylink_broadcast_label),
            checked = isActive,
            onToggle = { newValue ->
                coroutineScope.launch {
                    showMessage(if (newValue) startingText else stoppingText)
                    val success = if (newValue) {
                        feature.start()
                    } else {
                        feature.stop()
                    }
                    showMessage(if (success) (if (newValue) startedText else stoppedText) else (if (newValue) failedStartText else failedStopText))
                }
            }
        )

        Text(
            text = stringResource(Res.string.partylink_broadcast_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
} 