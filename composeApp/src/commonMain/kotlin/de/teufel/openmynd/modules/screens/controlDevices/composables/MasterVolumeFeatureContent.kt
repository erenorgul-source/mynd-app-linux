package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.MasterVolume
import de.teufel.openmynd.modules.core.feature.mastervolume.MasterVolumeFeature
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import de.teufel.openmynd.modules.screens.controlDevices.FeatureContent
import de.teufel.openmynd.modules.screens.controlDevices.composables.common.RangeSliderWithHeader
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.master_volume_title
import openmynd.composeapp.generated.resources.setting_volume_to_format
import openmynd.composeapp.generated.resources.volume_set_to_format
import openmynd.composeapp.generated.resources.failed_to_set_volume
import de.teufel.openmynd.utils.formatIndexed

/**
 * A composable that shows master volume controls, conditionally displayed when the feature is available.
 */
@Composable
fun MasterVolumeFeatureContent(viewModel: ControlDevicesViewModel) {
    FeatureContent<MasterVolumeFeature>(
        viewModel = viewModel,
        featureClass = MasterVolume::class
    ) { masterVolumeFeature ->
        MasterVolumeFeatureContentImpl(masterVolumeFeature) { message ->
            viewModel.showMessage(message)
        }
    }
}

/**
 * Implementation of the master volume feature content that directly uses the feature interface.
 */
@Composable
private fun MasterVolumeFeatureContentImpl(
    masterVolumeFeature: MasterVolumeFeature,
    showMessage: (String) -> Unit
) {
    // Collect state directly from the feature
    val volume by masterVolumeFeature.volume.collectAsState(initial = null)
    val volumeRange = masterVolumeFeature.range
    val coroutineScope = rememberCoroutineScope()
    
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        val settingTemplate = stringResource(Res.string.setting_volume_to_format)
        val setTemplate = stringResource(Res.string.volume_set_to_format)
        val failedSetText = stringResource(Res.string.failed_to_set_volume)
        RangeSliderWithHeader(
            title = stringResource(Res.string.master_volume_title),
            currentValue = volume,
            range = volumeRange,
            valueFormatter = { "$it%" },
            onValueCommitted = { newVolume ->
                coroutineScope.launch {
                    showMessage(formatIndexed(settingTemplate, newVolume))
                    val success = masterVolumeFeature.set(newVolume)
                    showMessage(if (success) formatIndexed(setTemplate, newVolume) else failedSetText)
                }
            }
        )
    }
} 