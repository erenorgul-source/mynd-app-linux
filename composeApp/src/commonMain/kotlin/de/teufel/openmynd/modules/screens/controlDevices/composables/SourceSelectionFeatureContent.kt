package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.SourceSelection
import de.teufel.openmynd.modules.core.feature.sourceselection.SourceSelectionFeature
import de.teufel.openmynd.modules.core.feature.sourceselection.SourceType
import de.teufel.openmynd.modules.screens.controlDevices.ControlDevicesViewModel
import de.teufel.openmynd.modules.screens.controlDevices.FeatureContent
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.source_selection_title
import openmynd.composeapp.generated.resources.setting_source_to_format
import openmynd.composeapp.generated.resources.source_set_to_format
import openmynd.composeapp.generated.resources.failed_to_set_source
import openmynd.composeapp.generated.resources.source_bluetooth
import openmynd.composeapp.generated.resources.source_aux
import openmynd.composeapp.generated.resources.source_usb
import de.teufel.openmynd.utils.formatIndexed

/**
 * A composable that shows source selection options, conditionally displayed when the feature is available.
 */
@Composable
fun SourceSelectionFeatureContent(viewModel: ControlDevicesViewModel) {
    FeatureContent<SourceSelectionFeature>(
        viewModel = viewModel,
        featureClass = SourceSelection::class
    ) { sourceSelectionFeature ->
        SourceSelectionFeatureContentImpl(sourceSelectionFeature) { message ->
            viewModel.showMessage(message)
        }
    }
}

/**
 * Implementation of the source selection feature content that directly uses the feature interface.
 */
@Composable
private fun SourceSelectionFeatureContentImpl(
    sourceSelectionFeature: SourceSelectionFeature,
    showMessage: (String) -> Unit
) {
    val currentSource by sourceSelectionFeature.currentSource.collectAsState()
    val connectedSources by sourceSelectionFeature.connectedSources.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()
    
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        val labelBluetooth = stringResource(Res.string.source_bluetooth)
        val labelAux = stringResource(Res.string.source_aux)
        val labelUsb = stringResource(Res.string.source_usb)
        val settingTemplate = stringResource(Res.string.setting_source_to_format)
        val setTemplate = stringResource(Res.string.source_set_to_format)
        val failedSetText = stringResource(Res.string.failed_to_set_source)
        Text(
            text = stringResource(Res.string.source_selection_title),
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Iterate through all possible sources
            SourceType.entries.forEachIndexed { index, sourceType ->
                val isConnected = connectedSources?.contains(sourceType) == true
                val isSelected = currentSource == sourceType
                
                ElevatedButton(
                    onClick = {
                        if (isConnected) {
                            coroutineScope.launch {
                                val displayName = when (sourceType) {
                                    SourceType.BLUETOOTH -> labelBluetooth
                                    SourceType.AUX -> labelAux
                                    SourceType.USB -> labelUsb
                                }
                                showMessage(formatIndexed(settingTemplate, displayName))
                                val success = sourceSelectionFeature.setSource(sourceType)
                                showMessage(if (success) formatIndexed(setTemplate, displayName) else failedSetText)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isConnected,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer 
                            isConnected -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.surface
                        },
                        contentColor = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                            isConnected -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                ) {
                    val name = when (sourceType) {
                        SourceType.BLUETOOTH -> labelBluetooth
                        SourceType.AUX -> labelAux
                        SourceType.USB -> labelUsb
                    }
                    Text(name)
                }
                
                if (index < SourceType.entries.size - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }
} 