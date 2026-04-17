package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.FirmwareVersion
import de.teufel.openmynd.modules.core.feature.McuFirmwareVersion
import de.teufel.openmynd.modules.core.feature.firmwareversion.FirmwareVersionFeature
import de.teufel.openmynd.modules.core.feature.mcufirmware.McuFirmwareVersionFeature
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import de.teufel.openmynd.modules.screens.controlDevices.FeatureContent
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.firmware_versions_title
import openmynd.composeapp.generated.resources.firmware_bt_label
import openmynd.composeapp.generated.resources.firmware_mcu_label
import openmynd.composeapp.generated.resources.firmware_dsp_label

@Composable
fun FirmwareVersionsFeatureContent(viewModel: ControlDevicesViewModel) {
    val isBtSupported by viewModel.isFeatureSupported(FirmwareVersion::class)
        .collectAsState(initial = false)

    if (isBtSupported) {
        FeatureContent<FirmwareVersionFeature>(
            viewModel = viewModel,
            featureClass = FirmwareVersion::class,
        ) { btFeature ->
            val isMcuSupported by viewModel.isFeatureSupported(McuFirmwareVersion::class)
                .collectAsState(initial = false)

            var mcuFeature: McuFirmwareVersionFeature? = null
            if (isMcuSupported) {
                mcuFeature = viewModel.getFeature(McuFirmwareVersion::class) as? McuFirmwareVersionFeature
            }

            LaunchedEffect(btFeature) { viewModel.refreshFeature(FirmwareVersion::class) }
            LaunchedEffect(mcuFeature) { if (mcuFeature != null) viewModel.refreshFeature(McuFirmwareVersion::class) }

            FirmwareVersionsFeatureContentImpl(btFeature, mcuFeature)
        }
    }
}

@Composable
private fun FirmwareVersionsFeatureContentImpl(
    btFeature: FirmwareVersionFeature,
    mcuFeature: McuFirmwareVersionFeature?
) {
    val btVersion by btFeature.version.collectAsState()

    val mcuVersion by mcuFeature?.mcuVersion?.collectAsState() ?: remember { mutableStateOf(null) }
    val dspVersion by mcuFeature?.dspVersion?.collectAsState() ?: remember { mutableStateOf(null) }
    val supportsDsp = mcuFeature?.targets?.containsKey("DSP") == true

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(Res.string.firmware_versions_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(Res.string.firmware_bt_label),
                style = MaterialTheme.typography.bodyMedium
            )
            if (btVersion != null) {
                Text(
                    text = btVersion!!,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }

        if (mcuFeature != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(Res.string.firmware_mcu_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (mcuVersion != null) {
                    Text(
                        text = mcuVersion!!,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }

            if (supportsDsp) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.firmware_dsp_label),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (dspVersion != null) {
                        Text(
                            text = dspVersion!!,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}