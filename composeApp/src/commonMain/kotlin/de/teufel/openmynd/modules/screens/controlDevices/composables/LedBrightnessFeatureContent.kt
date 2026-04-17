package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.LedBrightness
import de.teufel.openmynd.modules.core.feature.ledbrightness.LedBrightnessFeature
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import de.teufel.openmynd.modules.screens.controlDevices.FeatureContent
import de.teufel.openmynd.modules.screens.controlDevices.composables.common.RangeSliderWithHeader
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.led_brightness_title

@Composable
fun LedBrightnessFeatureContent(viewModel: ControlDevicesViewModel) {
    FeatureContent<LedBrightnessFeature>(
        viewModel = viewModel,
        featureClass = LedBrightness::class
    ) { ledBrightnessFeature ->
        LedBrightnessFeatureContentImpl(ledBrightnessFeature)
    }
}

@Composable
private fun LedBrightnessFeatureContentImpl(
    ledBrightnessFeature: LedBrightnessFeature,
) {
    val brightness by ledBrightnessFeature.brightness.collectAsState(initial = null)
    val brightnessRange = ledBrightnessFeature.range
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        RangeSliderWithHeader(
            title = stringResource(Res.string.led_brightness_title),
            currentValue = brightness,
            range = brightnessRange,
            onValueCommitted = { newBrightness ->
                coroutineScope.launch {
                    ledBrightnessFeature.set(newBrightness)
                }
            }
        )
    }
}
