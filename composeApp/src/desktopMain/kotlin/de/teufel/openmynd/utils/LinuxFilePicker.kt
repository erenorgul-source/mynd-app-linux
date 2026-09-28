package de.teufel.openmynd.utils

import de.teufel.openmynd.utils.portal.XdgDesktopPortal
import de.teufel.openmynd.utils.portal.XdgDesktopPortal.FileResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Opens a file using the desktop's native dialog through the XDG portal (the KDE dialog on
 * Plasma, GTK's on GNOME), falling back to AWT's dialog when no portal is running.
 */
class LinuxFilePicker(private val portal: XdgDesktopPortal = XdgDesktopPortal()) {

    /** Returns the selected file, or null if the user cancelled. */
    suspend fun pickFile(title: String): File? = when (val result = portal.openFile(title)) {
        is FileResult.Selected -> result.file
        FileResult.Cancelled -> null
        FileResult.Unavailable -> pickWithAwt(title)
    }

    private suspend fun pickWithAwt(title: String): File? = withContext(Dispatchers.IO) {
        var selected: File? = null
        EventQueue.invokeAndWait {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            dialog.isVisible = true
            selected = dialog.files.firstOrNull()
            dialog.dispose()
        }
        selected
    }
}
