package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.AutoOffTimer
import de.teufel.openmynd.modules.core.feature.autooff.AutoOffTimerFeature
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import de.teufel.openmynd.modules.screens.controlDevices.FeatureContent
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.auto_off_timer_title
import openmynd.composeapp.generated.resources.disabled
import openmynd.composeapp.generated.resources.minutes_format
import openmynd.composeapp.generated.resources.off_short
import openmynd.composeapp.generated.resources.minutes_short_format
import openmynd.composeapp.generated.resources.setting_auto_off_timer_to_format
import openmynd.composeapp.generated.resources.auto_off_timer_set_to_format
import openmynd.composeapp.generated.resources.failed_to_set_auto_off_timer
import de.teufel.openmynd.utils.formatIndexed

/**
 * A composable that shows auto-off timer options, conditionally displayed when the feature is available.
 */
@Composable
fun AutoOffTimerFeatureContent(viewModel: ControlDevicesViewModel) {
    FeatureContent<AutoOffTimerFeature>(
        viewModel = viewModel,
        featureClass = AutoOffTimer::class,
        dataReady = { it.seconds.collectAsState(initial = null).value != null }
    ) { feature ->
        AutoOffTimerFeatureContentImpl(feature) { message ->
            viewModel.showMessage(message)
        }
    }
}

/**
 * Implementation of the auto-off timer feature content that directly uses the feature interface.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoOffTimerFeatureContentImpl(
    autoOffTimerFeature: AutoOffTimerFeature,
    showMessage: (String) -> Unit
) {
    // Collect state directly from the feature
    val seconds by autoOffTimerFeature.seconds.collectAsState(initial = null)
    val supportedSeconds = autoOffTimerFeature.supportedSeconds
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        val offShortText = stringResource(Res.string.off_short)
        val minutesFormatTemplate = stringResource(Res.string.minutes_format)
        val settingTemplate = stringResource(Res.string.setting_auto_off_timer_to_format)
        val setTemplate = stringResource(Res.string.auto_off_timer_set_to_format)
        val failedSetText = stringResource(Res.string.failed_to_set_auto_off_timer)
        Text(
            text = stringResource(Res.string.auto_off_timer_title),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (seconds != null) {
            Text(
                text = if (seconds == 0) stringResource(Res.string.disabled) else stringResource(Res.string.minutes_format, seconds!! / 60),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = spacedBy(8.dp),
                verticalArrangement = spacedBy(8.dp),
                maxItemsInEachRow = Int.MAX_VALUE
            ) {
                supportedSeconds.sorted().forEach { timerSeconds ->
                    val label = if (timerSeconds == 0) offShortText else stringResource(Res.string.minutes_short_format, timerSeconds / 60)

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val minutesText = if (timerSeconds == 0) offShortText else formatIndexed(minutesFormatTemplate, (timerSeconds / 60))
                                showMessage(formatIndexed(settingTemplate, minutesText))
                                val success = autoOffTimerFeature.set(timerSeconds)
                                showMessage(
                                    if (success) formatIndexed(setTemplate, minutesText)
                                    else failedSetText
                                )
                            }
                        },
                        enabled = seconds != timerSeconds,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(label)
                    }
                }
            }
        } else {
            // Loading state for seconds value
            LinearProgressIndicator(
                progress = { 0.5f },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Show disabled buttons while loading
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = spacedBy(8.dp),
                verticalArrangement = spacedBy(8.dp),
                maxItemsInEachRow = Int.MAX_VALUE
            ) {
                supportedSeconds.sorted().forEach { timerSeconds ->
                    val label = if (timerSeconds == 0) stringResource(Res.string.off_short) else stringResource(Res.string.minutes_short_format, timerSeconds / 60)

                    Button(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
} 