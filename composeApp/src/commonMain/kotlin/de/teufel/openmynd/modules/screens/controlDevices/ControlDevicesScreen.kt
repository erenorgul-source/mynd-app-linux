package de.teufel.openmynd.modules.screens.controlDevices

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.teufel.openmynd.utils.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.navigator.internal.BackHandler
import co.touchlab.kermit.Logger
import de.teufel.openmynd.modules.screens.controlDevices.composables.*
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.device_control_title
import openmynd.composeapp.generated.resources.back_cd
import openmynd.composeapp.generated.resources.disconnect
import openmynd.composeapp.generated.resources.status_format
import openmynd.composeapp.generated.resources.device_disconnected_or_not_found

data class ControlDeviceScreen(
    val deviceAddress: String
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class, InternalVoyagerApi::class)
    @Composable
    override fun Content() {
        val logger = remember { Logger.withTag("ControlDeviceScreen") }
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: ControlDevicesViewModel = koinInject()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        BackHandler(enabled = true) { viewModel.disconnectDevice() }

        LaunchedEffect(uiState.error) {
            uiState.error?.let {
                scope.launch { snackbarHostState.showSnackbar(it) }
            }
        }

        LaunchedEffect(Unit) {
            viewModel.navigationEvent.collect { event ->
                when (event) {
                    is ControlDevicesViewModel.NavigationEvent.NavigateBack -> {
                        logger.d { "Received NavigateBack event, popping navigator." }
                        navigator.pop()
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            viewModel.prefetchSupportedFeatures()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(uiState.deviceName ?: stringResource(Res.string.device_control_title)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.disconnectDevice() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back_cd))
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Surface(tonalElevation = 4.dp) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        onClick = { viewModel.disconnectDevice() }
                    ) { Text(stringResource(Res.string.disconnect)) }
                }
            }
        ) { paddingValues ->
            val deviceName = uiState.deviceName
            
            if (uiState.isLoading && deviceName == null) { // Show loading if name isn't available yet
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (deviceName != null) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                     ActionsUpgradeFeatureContent(viewModel)
                     AutoOffTimerFeatureContent(viewModel)
                     BatteryCapacityFeatureContent(viewModel)
                     BatteryFeatureContent(viewModel)
                     BatteryFriendlyChargingFeatureContent(viewModel)
                     DeviceColorFeatureContent(viewModel)
                     EcoModeFeatureContent(viewModel)
                     EqGainFeatureContent(viewModel)
                     FirmwareVersionsFeatureContent(viewModel)
                     LedBrightnessFeatureContent(viewModel)
                     MasterMuteFeatureContent(viewModel)
                     MasterVolumeFeatureContent(viewModel)
                     MultipointFeatureContent(viewModel)
                     PartyLinkBroadcastFeatureContent(viewModel)
                     SoundIconsFeatureContent(viewModel)
                     SourceSelectionFeatureContent(viewModel)

                    if (uiState.lastMessage != null) {
                        Text(stringResource(Res.string.status_format, uiState.lastMessage!!), style = MaterialTheme.typography.bodySmall)
                    }
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(uiState.error ?: stringResource(Res.string.device_disconnected_or_not_found))
                }
            }
        }
    }
}
