package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.Battery
import de.teufel.openmynd.modules.core.feature.ChargingStatus
import de.teufel.openmynd.modules.core.feature.battery.BatteryFeature
import de.teufel.openmynd.modules.core.feature.chargingstatus.ChargingStatusFeature
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import de.teufel.openmynd.modules.screens.controlDevices.FeatureContent
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.battery_title
import openmynd.composeapp.generated.resources.charging
import openmynd.composeapp.generated.resources.not_charging
import openmynd.composeapp.generated.resources.loading_battery_status

/**
 * A composable that shows battery information, conditionally displayed when the feature is available.
 */
@Composable
fun BatteryFeatureContent(viewModel: ControlDevicesViewModel) {
    val isBatterySupported by viewModel.isFeatureSupported(Battery::class)
        .collectAsState(initial = false)
    val isChargingSupported by viewModel.isFeatureSupported(ChargingStatus::class)
        .collectAsState(initial = false)


    if (isBatterySupported) {
        FeatureContent<BatteryFeature>(
            viewModel = viewModel,
            featureClass = Battery::class,
            dataReady = { it.level.collectAsState(initial = null).value != null }
        ) { batteryFeature ->
            if (isChargingSupported) {
                val chargingFeature =
                    viewModel.getFeature(ChargingStatus::class) as? ChargingStatusFeature
                if (chargingFeature != null) {
                    BatteryFeatureContentImpl(batteryFeature, chargingFeature)
                } else {
                    BatteryFeatureContentImpl(batteryFeature, null)
                }
            } else {
                BatteryFeatureContentImpl(batteryFeature, null)
            }
        }
    }
}

/**
 * Implementation of the battery feature content that directly uses the feature interfaces.
 */
@Composable
private fun BatteryFeatureContentImpl(
    batteryFeature: BatteryFeature,
    chargingFeature: ChargingStatusFeature? = null
) {
    val batteryLevel by batteryFeature.level.collectAsState(initial = null)

    val chargingState = remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(chargingFeature) {
        chargingFeature?.isConnected?.collect { newState ->
            chargingState.value = newState
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = stringResource(Res.string.battery_title),
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            if (batteryLevel != null) {
                BatteryIndicator(
                    level = batteryLevel!!,
                    isCharging = chargingState.value == true,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "$batteryLevel%",
                        style = MaterialTheme.typography.titleSmall
                    )

                    if (chargingState.value != null) {
                        Text(
                            text = if (chargingState.value == true) stringResource(Res.string.charging) else stringResource(Res.string.not_charging),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (chargingState.value == true) Color.Green else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                // Show loading indicator when battery level is null
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = stringResource(Res.string.loading_battery_status),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun BatteryIndicator(
    level: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(2.dp)
    ) {
        // Battery fill
        Box(
            modifier = Modifier
                .fillMaxWidth(level / 100f)
                .fillMaxHeight()
                .background(
                    color = when {
                        level <= 20 -> Color.Red
                        level <= 50 -> Color.Yellow
                        else -> Color.Green
                    },
                    shape = RoundedCornerShape(2.dp)
                )
        )

        // Charging indicator
        if (isCharging) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(Res.string.charging),
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(24.dp)
            )
        }
    }
}