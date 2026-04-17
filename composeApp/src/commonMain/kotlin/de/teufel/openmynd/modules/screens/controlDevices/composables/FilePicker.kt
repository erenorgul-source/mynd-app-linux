package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.runtime.Composable

/**
 * Platform-specific file picker launcher.
 *
 * Returns a lambda that, when invoked, opens the native file picker.
 * When the user selects a file, [onFilePicked] is called with the file name
 * and its raw content bytes. If selection is cancelled, nothing is called.
 */
@Composable
expect fun rememberFilePickerLauncher(
    onFilePicked: (fileName: String, content: ByteArray) -> Unit
): () -> Unit
