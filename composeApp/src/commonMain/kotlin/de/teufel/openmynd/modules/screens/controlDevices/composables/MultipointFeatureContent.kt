package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.Multipoint
import de.teufel.openmynd.modules.core.feature.multipoint.MultipointFeature
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import de.teufel.openmynd.modules.screens.controlDevices.FeatureContent
import de.teufel.openmynd.modules.screens.controlDevices.composables.common.FeatureSectionHeader
import de.teufel.openmynd.modules.screens.controlDevices.composables.common.ToggleRow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.multipoint_title
import openmynd.composeapp.generated.resources.multipoint_label
import openmynd.composeapp.generated.resources.multipoint_desc
import openmynd.composeapp.generated.resources.enabling_multipoint
import openmynd.composeapp.generated.resources.disabling_multipoint
import openmynd.composeapp.generated.resources.multipoint_enabled
import openmynd.composeapp.generated.resources.multipoint_disabled
import openmynd.composeapp.generated.resources.multipoint_failed_enable
import openmynd.composeapp.generated.resources.multipoint_failed_disable

/**
 * A composable that shows multipoint connectivity options, conditionally displayed when the feature is available.
 */
@Composable
fun MultipointFeatureContent(viewModel: ControlDevicesViewModel) {
    FeatureContent<MultipointFeature>(
        viewModel = viewModel,
        featureClass = Multipoint::class
    ) { multipointFeature ->
        MultipointFeatureContentImpl(multipointFeature) { message ->
            viewModel.showMessage(message)
        }
    }
}

/**
 * Implementation of the multipoint feature content that directly uses the feature interface.
 */
@Composable
private fun MultipointFeatureContentImpl(
    multipointFeature: MultipointFeature,
    showMessage: (String) -> Unit
) {
    // Collect state directly from the feature
    val enabled by multipointFeature.enabled.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        val enablingText = stringResource(Res.string.enabling_multipoint)
        val disablingText = stringResource(Res.string.disabling_multipoint)
        val enabledText = stringResource(Res.string.multipoint_enabled)
        val disabledText = stringResource(Res.string.multipoint_disabled)
        val failedEnableText = stringResource(Res.string.multipoint_failed_enable)
        val failedDisableText = stringResource(Res.string.multipoint_failed_disable)
        FeatureSectionHeader(title = stringResource(Res.string.multipoint_title))

        Spacer(modifier = Modifier.height(4.dp))

        ToggleRow(
            label = stringResource(Res.string.multipoint_label),
            checked = enabled,
            onToggle = { newValue ->
                coroutineScope.launch {
                    showMessage(if (newValue) enablingText else disablingText)
                    val success = if (newValue) {
                        multipointFeature.enable()
                    } else {
                        multipointFeature.disable()
                    }
                    showMessage(if (success) (if (newValue) enabledText else disabledText) else (if (newValue) failedEnableText else failedDisableText))
                }
            }
        )

        Text(
            text = stringResource(Res.string.multipoint_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
} 