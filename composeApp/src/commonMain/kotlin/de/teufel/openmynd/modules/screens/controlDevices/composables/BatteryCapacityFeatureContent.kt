package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.BatteryCapacity
import de.teufel.openmynd.modules.core.feature.battery.BatteryCapacityFeature
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import de.teufel.openmynd.modules.screens.controlDevices.FeatureContent
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.battery_capacity_title
import openmynd.composeapp.generated.resources.current_capacity_label
import openmynd.composeapp.generated.resources.maximum_capacity_label
import openmynd.composeapp.generated.resources.capacity_mah_format
import openmynd.composeapp.generated.resources.battery_health_label
import openmynd.composeapp.generated.resources.battery_capacity_desc

@Composable
fun BatteryCapacityFeatureContent(viewModel: ControlDevicesViewModel) {

    FeatureContent<BatteryCapacityFeature>(
        viewModel = viewModel,
        featureClass = BatteryCapacity::class
    ) { feature ->

        val currentCapacity by feature.currentCapacity.collectAsState()
        val maxCapacity by feature.maxCapacity.collectAsState()

        LaunchedEffect(feature) {
            viewModel.refreshFeature(BatteryCapacity::class)
        }

        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.battery_capacity_title),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (currentCapacity != null && maxCapacity != null) {
                val healthPercentage = (currentCapacity!!.toFloat() / maxCapacity!! * 100).toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.current_capacity_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(Res.string.capacity_mah_format, currentCapacity!!),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.maximum_capacity_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(Res.string.capacity_mah_format, maxCapacity!!),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.battery_health_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$healthPercentage%",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { healthPercentage / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(Res.string.battery_capacity_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
} 