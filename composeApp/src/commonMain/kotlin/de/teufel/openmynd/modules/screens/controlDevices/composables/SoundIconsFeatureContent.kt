package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.SoundIcons
import de.teufel.openmynd.modules.core.feature.soundicons.SoundIconsFeature
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import de.teufel.openmynd.modules.screens.controlDevices.FeatureContent
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.sound_icons_title
import openmynd.composeapp.generated.resources.sound_icons_label
import openmynd.composeapp.generated.resources.setting_sound_icons_to_enabled
import openmynd.composeapp.generated.resources.setting_sound_icons_to_disabled
import openmynd.composeapp.generated.resources.sound_icons_enabled
import openmynd.composeapp.generated.resources.sound_icons_disabled
import openmynd.composeapp.generated.resources.failed_to_change_sound_icons

/**
 * A composable that shows sound icons toggle, conditionally displayed when the feature is available.
 */
@Composable
fun SoundIconsFeatureContent(viewModel: ControlDevicesViewModel) {
    FeatureContent<SoundIconsFeature>(
        viewModel    = viewModel,
        featureClass = SoundIcons::class,
        dataReady    = { it.enabled.collectAsState(initial = null).value != null }
    ) { soundIconsFeature ->
        SoundIconsFeatureContentImpl(soundIconsFeature) { message ->
            viewModel.showMessage(message)
        }
    }
}

/**
 * Implementation of the sound icons feature content that directly uses the feature interface.
 */
@Composable
private fun SoundIconsFeatureContentImpl(
    soundIconsFeature: SoundIconsFeature,
    showMessage: (String) -> Unit
) {
    val isSoundIconsEnabled by soundIconsFeature.enabled.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = stringResource(Res.string.sound_icons_title),
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(Res.string.sound_icons_label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            
            if (isSoundIconsEnabled != null) {
                val settingEnabled = stringResource(Res.string.setting_sound_icons_to_enabled)
                val settingDisabled = stringResource(Res.string.setting_sound_icons_to_disabled)
                val enabledText = stringResource(Res.string.sound_icons_enabled)
                val disabledText = stringResource(Res.string.sound_icons_disabled)
                val failedText = stringResource(Res.string.failed_to_change_sound_icons)

                Switch(
                    checked = isSoundIconsEnabled!!,
                    onCheckedChange = { isEnabled ->
                        coroutineScope.launch {
                             showMessage(if (isEnabled) settingEnabled else settingDisabled)
                            val success = if (isEnabled) {
                                soundIconsFeature.enable()
                            } else {
                                soundIconsFeature.disable()
                            }
                            showMessage(if (success) (if (isEnabled) enabledText else disabledText) else failedText)
                        }
                    }
                )
            } else {
                // Show loading switch
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
} 