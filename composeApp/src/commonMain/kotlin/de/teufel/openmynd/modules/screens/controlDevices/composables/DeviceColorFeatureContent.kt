package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.device.TeufelDeviceColor
import de.teufel.openmynd.modules.core.feature.DeviceColor
import de.teufel.openmynd.modules.core.feature.devicecolor.DeviceColorFeature
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import de.teufel.openmynd.modules.screens.controlDevices.FeatureContent
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.device_color_title
import openmynd.composeapp.generated.resources.color_id_format
import openmynd.composeapp.generated.resources.paren_format
import openmynd.composeapp.generated.resources.unknown

@Composable
fun DeviceColorFeatureContent(viewModel: ControlDevicesViewModel) {

    FeatureContent<DeviceColorFeature>(
        viewModel = viewModel,
        featureClass = DeviceColor::class,
        dataReady = { it.colorId.collectAsState(initial = null).value != null }
    ) { feature ->

        val colorId by feature.colorId.collectAsState()

        LaunchedEffect(feature) {
            viewModel.refreshFeature(DeviceColor::class)
        }

        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.device_color_title),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.color_id_format, (colorId?.toString() ?: stringResource(Res.string.unknown))),
                    style = MaterialTheme.typography.bodyMedium
                )

                colorId?.let { id ->
                    val colorName = TeufelDeviceColor.of(id).name

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = stringResource(Res.string.paren_format, colorName),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
} 