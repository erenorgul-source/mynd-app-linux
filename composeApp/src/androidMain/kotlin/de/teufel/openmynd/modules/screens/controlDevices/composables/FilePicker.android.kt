package de.teufel.openmynd.modules.screens.controlDevices.composables

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFilePickerLauncher(
    onFilePicked: (fileName: String, content: ByteArray) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val contentResolver = context.contentResolver

        val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: uri.lastPathSegment ?: "firmware.bin"

        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes != null) {
            onFilePicked(fileName, bytes)
        }
    }
    return { launcher.launch("*/*") }
}
