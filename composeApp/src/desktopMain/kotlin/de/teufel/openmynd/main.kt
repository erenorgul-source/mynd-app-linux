package de.teufel.openmynd

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.teufel.openmynd.di.initKoin
import de.teufel.openmynd.modules.ui.theme.LinuxTheme
import de.teufel.openmynd.utils.LinuxDesktopIntegration
import org.jetbrains.skia.Image
import java.awt.Dimension

fun main() {
    LinuxDesktopIntegration.configureBeforeAwt()
    initKoin()
    LinuxTheme.start()
    val uiScale = LinuxDesktopIntegration.extraUiScale()
    val icon = loadAppIcon()

    application {
        val windowState = rememberWindowState(
            size = DpSize(440.dp * uiScale, 860.dp * uiScale),
            position = WindowPosition(Alignment.Center),
        )
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "OpenMynd",
            icon = icon,
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension((360 * uiScale).toInt(), (560 * uiScale).toInt())
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density * uiScale, density.fontScale)) {
                App()
            }
        }
    }
}

private fun loadAppIcon(): Painter? =
    object {}.javaClass.getResourceAsStream("/openmynd.png")?.use { stream ->
        BitmapPainter(Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap())
    }
