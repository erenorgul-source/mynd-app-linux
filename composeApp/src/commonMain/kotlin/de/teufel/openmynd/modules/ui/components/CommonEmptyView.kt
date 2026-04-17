package de.teufel.openmynd.modules.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.teufel.openmynd.modules.ui.IUiState
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.no_known_devices
import openmynd.composeapp.generated.resources.something_went_wrong_try_again
import openmynd.composeapp.generated.resources.loading

@Composable
fun CommonEmptyView(uiState: IUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = when {
                uiState.isEmpty -> stringResource(Res.string.no_known_devices)
                uiState.isError -> stringResource(Res.string.something_went_wrong_try_again)
                else -> stringResource(Res.string.loading)
            },
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .wrapContentSize(),
            style = MaterialTheme.typography.titleSmall
        )
    }
}