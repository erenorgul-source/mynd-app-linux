package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.EcoMode
import de.teufel.openmynd.modules.core.feature.ecomode.EcoModeFeature
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import de.teufel.openmynd.modules.screens.controlDevices.FeatureContent
import de.teufel.openmynd.modules.screens.controlDevices.composables.common.FeatureSectionHeader
import de.teufel.openmynd.modules.screens.controlDevices.composables.common.ToggleRow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.eco_mode_title
import openmynd.composeapp.generated.resources.energy_saving_mode_label
import openmynd.composeapp.generated.resources.eco_mode_desc
import openmynd.composeapp.generated.resources.eco_mode_enabled
import openmynd.composeapp.generated.resources.eco_mode_disabled
import openmynd.composeapp.generated.resources.eco_mode_failed_enable
import openmynd.composeapp.generated.resources.eco_mode_failed_disable

@Composable
fun EcoModeFeatureContent(viewModel: ControlDevicesViewModel) {

    FeatureContent<EcoModeFeature>(
        viewModel = viewModel,
        featureClass = EcoMode::class
    ) { feature ->

        val isEnabled by feature.isEnabled.collectAsState()

        val scope = rememberCoroutineScope()

        LaunchedEffect(feature) {
            viewModel.refreshFeature(EcoMode::class)
        }

        Column(modifier = Modifier.padding(16.dp)) {
            val ecoEnabledText = stringResource(Res.string.eco_mode_enabled)
            val ecoDisabledText = stringResource(Res.string.eco_mode_disabled)
            val ecoFailedEnableText = stringResource(Res.string.eco_mode_failed_enable)
            val ecoFailedDisableText = stringResource(Res.string.eco_mode_failed_disable)
            FeatureSectionHeader(title = stringResource(Res.string.eco_mode_title))

            Spacer(modifier = Modifier.height(8.dp))

            ToggleRow(
                label = stringResource(Res.string.energy_saving_mode_label),
                checked = isEnabled,
                onToggle = { newValue ->
                    scope.launch {
                        val success = if (newValue) feature.enable() else feature.disable()
                        if (success) {
                            viewModel.showMessage(if (newValue) ecoEnabledText else ecoDisabledText)
                        } else {
                            viewModel.showMessage(if (newValue) ecoFailedEnableText else ecoFailedDisableText)
                        }
                    }
                }
            )

            Text(
                text = stringResource(Res.string.eco_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
} 