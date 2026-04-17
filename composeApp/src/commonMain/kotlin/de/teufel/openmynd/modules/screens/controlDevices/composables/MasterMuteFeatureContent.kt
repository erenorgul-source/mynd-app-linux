package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.MasterMute
import de.teufel.openmynd.modules.core.feature.mastermute.MasterMuteFeature
import co.touchlab.kermit.Logger
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import de.teufel.openmynd.modules.screens.controlDevices.FeatureContent
import de.teufel.openmynd.modules.screens.controlDevices.composables.common.FeatureSectionHeader
import de.teufel.openmynd.modules.screens.controlDevices.composables.common.ToggleRow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.master_mute_title
import openmynd.composeapp.generated.resources.mute_audio_output_label
import openmynd.composeapp.generated.resources.device_muted
import openmynd.composeapp.generated.resources.device_unmuted
import openmynd.composeapp.generated.resources.failed_to_mute_device
import openmynd.composeapp.generated.resources.failed_to_unmute_device
import openmynd.composeapp.generated.resources.quick_silence_desc

/**
 * A composable that shows master mute controls, conditionally displayed when the feature is available.
 */
@Composable
fun MasterMuteFeatureContent(viewModel: ControlDevicesViewModel) {
    val logger = Logger.withTag("MasterMuteUI")
    logger.v { "Checking if feature is supported" }

    // Log if the feature is supported before trying to load it
    val isSupported by viewModel.isFeatureSupported(MasterMute::class)
        .collectAsState(initial = false)
    logger.v { "MasterMute feature supported = $isSupported" }

    FeatureContent<MasterMuteFeature>(
        viewModel = viewModel,
        featureClass = MasterMute::class
    ) { feature ->
        logger.v { "Feature loaded successfully" }

        // Collect the current state
        val isMuted by feature.isMuted.collectAsState()
        logger.v { "Current mute state = $isMuted" }

        val scope = rememberCoroutineScope()

        // Force refresh the mute value
        LaunchedEffect(feature) {
            logger.v { "Requesting refresh of mute state" }
            viewModel.refreshFeature(MasterMute::class)
        }
        Column(modifier = Modifier.padding(16.dp)) {
            val mutedText = stringResource(Res.string.device_muted)
            val unmutedText = stringResource(Res.string.device_unmuted)
            val failedMuteText = stringResource(Res.string.failed_to_mute_device)
            val failedUnmuteText = stringResource(Res.string.failed_to_unmute_device)
            FeatureSectionHeader(title = stringResource(Res.string.master_mute_title))

            Spacer(modifier = Modifier.height(8.dp))

            ToggleRow(
                label = stringResource(Res.string.mute_audio_output_label),
                checked = isMuted,
                onToggle = { newValue ->
                    logger.d { "Setting mute to $newValue" }
                    scope.launch {
                        val success = feature.set(newValue)
                        if (success) {
                            logger.v { "Successfully set mute to $newValue" }
                            viewModel.showMessage(if (newValue) mutedText else unmutedText)
                        } else {
                            logger.w { "Failed to set mute to $newValue" }
                            viewModel.showMessage(if (newValue) failedMuteText else failedUnmuteText)
                        }
                    }
                }
            )

            Text(
                text = stringResource(Res.string.quick_silence_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
} 