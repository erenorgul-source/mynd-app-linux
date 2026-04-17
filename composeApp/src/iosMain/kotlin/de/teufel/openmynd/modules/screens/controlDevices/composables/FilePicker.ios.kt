package de.teufel.openmynd.modules.screens.controlDevices.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.lastPathComponent
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun rememberFilePickerLauncher(
    onFilePicked: (fileName: String, content: ByteArray) -> Unit
): () -> Unit {
    val callback = remember { { name: String, bytes: ByteArray -> onFilePicked(name, bytes) } }
    // Strong reference to prevent Kotlin/Native GC from collecting the delegate
    // while the picker is presented (UIKit holds only a weak ref to delegate).
    val activeDelegateRef = remember { mutableListOf<NSObject>() }

    return remember {
        {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData))

            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentsAtURLs: List<*>
                ) {
                    val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return

                    val accessing = url.startAccessingSecurityScopedResource()
                    try {
                        val data = NSData.dataWithContentsOfURL(url) ?: return
                        val bytes = ByteArray(data.length.toInt())
                        bytes.usePinned { pinned ->
                            memcpy(pinned.addressOf(0), data.bytes, data.length)
                        }
                        val name = url.lastPathComponent ?: "firmware.bin"
                        callback(name, bytes)
                    } finally {
                        if (accessing) url.stopAccessingSecurityScopedResource()
                        activeDelegateRef.clear()
                    }
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    activeDelegateRef.clear()
                }
            }

            activeDelegateRef.clear()
            activeDelegateRef.add(delegate)

            picker.delegate = delegate
            picker.allowsMultipleSelection = false

            val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
            rootVC?.presentViewController(picker, animated = true, completion = null)
        }
    }
}
