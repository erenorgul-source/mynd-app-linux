package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.BatteryFriendlyCharging
import de.teufel.openmynd.modules.core.feature.batteryfriendly.BatteryFriendlyChargingFeature
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import de.teufel.openmynd.modules.screens.controlDevices.FeatureContent
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.battery_friendly_charging_title
import openmynd.composeapp.generated.resources.battery_friendly_mode_label
import openmynd.composeapp.generated.resources.battery_friendly_desc1
import openmynd.composeapp.generated.resources.battery_friendly_desc2
import openmynd.composeapp.generated.resources.battery_friendly_enabled
import openmynd.composeapp.generated.resources.battery_friendly_disabled
import openmynd.composeapp.generated.resources.battery_friendly_failed_enable
import openmynd.composeapp.generated.resources.battery_friendly_failed_disable

@Composable
fun BatteryFriendlyChargingFeatureContent(viewModel: ControlDevicesViewModel) {

    FeatureContent<BatteryFriendlyChargingFeature>(
        viewModel = viewModel,
        featureClass = BatteryFriendlyCharging::class
    ) { feature ->

        val isEnabled by feature.isEnabled.collectAsState()

        val scope = rememberCoroutineScope()

        LaunchedEffect(feature) {
            viewModel.refreshFeature(BatteryFriendlyCharging::class)
        }

        val title = stringResource(Res.string.battery_friendly_charging_title)
        val modeLabel = stringResource(Res.string.battery_friendly_mode_label)
        val desc1 = stringResource(Res.string.battery_friendly_desc1)
        val desc2 = stringResource(Res.string.battery_friendly_desc2)
        val enabledMsg = stringResource(Res.string.battery_friendly_enabled)
        val disabledMsg = stringResource(Res.string.battery_friendly_disabled)
        val failedEnableMsg = stringResource(Res.string.battery_friendly_failed_enable)
        val failedDisableMsg = stringResource(Res.string.battery_friendly_failed_disable)

        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.bodyMedium
                )

                Switch(
                    checked = isEnabled == true,
                    onCheckedChange = { newValue ->
                        scope.launch {
                            val success = if (newValue) feature.enable() else feature.disable()
                            if (success) {
                                viewModel.showMessage(if (newValue) enabledMsg else disabledMsg)
                            } else {
                                viewModel.showMessage(if (newValue) failedEnableMsg else failedDisableMsg)
                            }
                        }
                    },
                    enabled = isEnabled != null
                )
            }

            Text(
                text = desc1,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = desc2,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
} 