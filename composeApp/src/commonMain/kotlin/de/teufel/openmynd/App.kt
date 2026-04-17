package de.teufel.openmynd

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import de.teufel.openmynd.modules.ui.theme.AppTheme
import de.teufel.openmynd.modules.navigation.RootScreen
import org.jetbrains.compose.ui.tooling.preview.Preview
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.request.crossfade
import coil3.util.DebugLogger
import coil3.compose.setSingletonImageLoaderFactory
import de.teufel.openmynd.modules.screens.permissions.PermissionsRequired

@OptIn(ExperimentalCoilApi::class)
@Composable
fun App() {
    AppTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            PermissionsRequired(
                onPermissionsGranted = {
                    Navigator(RootScreen)
                }
            )
        }
    }

    /**
     * Set the singleton image loader factory for coil3
     */
    setSingletonImageLoaderFactory { context ->
        getAsyncImageLoader(context)
    }
}

/**
 * Get the async image loader of coil3
 */
private fun getAsyncImageLoader(context: PlatformContext) =
    ImageLoader.Builder(context).crossfade(true).logger(DebugLogger()).build()