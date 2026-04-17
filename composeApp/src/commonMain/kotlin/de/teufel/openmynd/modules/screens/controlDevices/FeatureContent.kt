package de.teufel.openmynd.modules.screens.controlDevices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teufel.openmynd.modules.core.feature.*
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Default retry configuration for feature data loading
 */
data class RetryConfig(
    val initialDelayMs: Long = 100,
    val startBackOffDelayMs: Long = 500,
    val maxBackOffDelayMs: Long = 10000,
    val maxAttempts: Int = 10,
    val backoffFactor: Float = 1.5f
)

/**
 * Composable that checks if a feature is supported and gets the feature implementation,
 * then renders the content if available. Includes retry with exponential backoff for null values.
 */
@Composable
inline fun <reified T> FeatureContent(
    viewModel: ControlDevicesViewModel,
    featureClass: KClass<out Feature>,
    crossinline dataReady: @Composable (T) -> Boolean = { true },
    crossinline loadingState: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical=16.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    },
    crossinline content: @Composable (T) -> Unit
) {
    val providerIsLoading by remember {
        viewModel.uiState
            .map { it.isLoading }
            .distinctUntilChanged()
    }.collectAsState(initial = true)

    if (providerIsLoading) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            loadingState()
        }
        return
    }

    val isSupported by remember(featureClass) {
        viewModel.isFeatureSupported(featureClass).distinctUntilChanged()
    }.collectAsState(initial = false)
    if (!isSupported) return

    // Hold on to the last non-null feature instance so content doesn't thrash
    val currentFeature = viewModel.getFeature<T>(featureClass)
    var rememberedFeature by remember(featureClass) { mutableStateOf<T?>(null) }
    if (currentFeature != null) rememberedFeature = currentFeature
    val featureInstance: T? = rememberedFeature
    val isDataActuallyReady = featureInstance?.let { dataReady(it) } == true

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (isDataActuallyReady) {
            content(featureInstance)
        } else {
            loadingState()

            LaunchedEffect(featureClass) {
                try {
                    viewModel.refreshFeature(featureClass)
                } catch (_: Exception) { }
            }
        }
    }
}