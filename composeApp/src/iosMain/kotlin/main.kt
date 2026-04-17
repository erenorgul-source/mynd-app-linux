import androidx.compose.ui.window.ComposeUIViewController
import de.teufel.openmynd.App
import de.teufel.openmynd.di.initKoin
import platform.UIKit.UIViewController

@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    initKoin(connectorBackend = null)
    App()
}
