package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import de.teufel.openmynd.utils.LinuxFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.select_firmware_file
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun rememberFilePickerLauncher(
    onFilePicked: (fileName: String, content: ByteArray) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnFilePicked by rememberUpdatedState(onFilePicked)
    val title by rememberUpdatedState(stringResource(Res.string.select_firmware_file))
    val picker = remember { LinuxFilePicker() }
    return remember(scope, picker) {
        val launch: () -> Unit = {
            scope.launch {
                val file = picker.pickFile(title) ?: return@launch
                val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() } ?: return@launch
                currentOnFilePicked(file.name, bytes)
            }
        }
        launch
    }
}
