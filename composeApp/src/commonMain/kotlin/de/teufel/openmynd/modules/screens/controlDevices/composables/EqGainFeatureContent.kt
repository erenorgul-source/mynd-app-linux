package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.EqBand
import de.teufel.openmynd.modules.core.feature.EqGain
import de.teufel.openmynd.modules.core.feature.eqgain.EqGainFeature
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import de.teufel.openmynd.modules.screens.controlDevices.FeatureContent
import de.teufel.openmynd.utils.formatIndexed
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.equalizer_title
import openmynd.composeapp.generated.resources.eq_band_bass
import openmynd.composeapp.generated.resources.eq_band_mid
import openmynd.composeapp.generated.resources.eq_band_treble
import openmynd.composeapp.generated.resources.gain_value_signed_format
import openmynd.composeapp.generated.resources.adjust_frequency_response_format
import openmynd.composeapp.generated.resources.setting_eq_band_to_format
import openmynd.composeapp.generated.resources.eq_band_set_to_format
import openmynd.composeapp.generated.resources.failed_to_set_eq_band_format
import openmynd.composeapp.generated.resources.unknown

@Composable
fun EqGainFeatureContent(viewModel: ControlDevicesViewModel) {

    FeatureContent<EqGainFeature>(
        viewModel = viewModel,
        featureClass = EqGain::class,
        dataReady = {
            val gains by it.bandGains.collectAsState(initial = emptyMap())
            gains.keys.containsAll(it.supportedBands) && gains.values.all { v -> v != null }
        }
    ) { feature ->
        EqGainFeatureContentImpl(feature) { message ->
            viewModel.showMessage(message)
        }

        LaunchedEffect(feature) {
            viewModel.refreshFeature(EqGain::class)
        }
    }
}

@Composable
private fun EqGainFeatureContentImpl(
    feature: EqGainFeature,
    showMessage: (String) -> Unit,
) {
    val bandGains by feature.bandGains.collectAsState()

    val scope = rememberCoroutineScope()
    val sliderValues = remember { mutableStateMapOf<EqBand, Int>() }
    val isDragging = remember { mutableStateMapOf<EqBand, Boolean>() }

    val settingTemplate = stringResource(Res.string.setting_eq_band_to_format)
    val setTemplate = stringResource(Res.string.eq_band_set_to_format)
    val failedTemplate = stringResource(Res.string.failed_to_set_eq_band_format)

    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = stringResource(Res.string.equalizer_title),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        feature.supportedBands.forEach { band ->
            val bandName = when (band) {
                is EqBand.Bass -> stringResource(Res.string.eq_band_bass)
                is EqBand.Mid -> stringResource(Res.string.eq_band_mid)
                is EqBand.Treble -> stringResource(Res.string.eq_band_treble)
                else -> stringResource(Res.string.unknown)
            }

            val currentGain = bandGains[band] ?: 0
            val localValue = sliderValues[band]
            val dragging = isDragging[band] == true
            val displayValue = when {
                localValue == null -> currentGain
                dragging -> localValue
                else -> {
                    sliderValues[band] = currentGain
                    currentGain
                }
            }

            BandSlider(
                bandName = bandName,
                currentGain = displayValue,
                gainRange = feature.gainRange,
                onGainChange = { newGain ->
                    sliderValues[band] = newGain
                    isDragging[band] = true
                },
                onGainChangeFinished = {
                    val finalValue = sliderValues[band] ?: currentGain
                    isDragging[band] = false
                    val signed = if (finalValue > 0) "+$finalValue" else "$finalValue"
                    showMessage(formatIndexed(settingTemplate, bandName, signed))
                    scope.launch {
                        val ok = feature.set(band, finalValue)
                        showMessage(
                            if (ok) formatIndexed(setTemplate, bandName, signed)
                            else formatIndexed(failedTemplate, bandName)
                        )
                    }
                }
            )

            if (band != feature.supportedBands.last()) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.adjust_frequency_response_format, feature.gainRange.first, feature.gainRange.last),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun BandSlider(
    bandName: String,
    currentGain: Int,
    gainRange: IntRange,
    onGainChange: (Int) -> Unit,
    onGainChangeFinished: () -> Unit = {}
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = bandName,
                style = MaterialTheme.typography.bodyMedium
            )

            val signed = if (currentGain > 0) "+$currentGain" else "$currentGain"
            Text(
                text = stringResource(Res.string.gain_value_signed_format, signed),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = currentGain.toFloat(),
            onValueChange = { sliderValue ->
                onGainChange(sliderValue.roundToInt())
            },
            onValueChangeFinished = onGainChangeFinished,
            valueRange = gainRange.first.toFloat()..gainRange.last.toFloat(),
            steps = gainRange.last - gainRange.first - 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
