package de.teufel.openmynd.modules.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import org.jetbrains.compose.resources.stringResource
import openmynd.composeapp.generated.resources.Res
import openmynd.composeapp.generated.resources.favorites_tab_title
import openmynd.composeapp.generated.resources.scan_tab_title
import de.teufel.openmynd.modules.screens.knownDevices.KnownDevicesMainScreen
import de.teufel.openmynd.modules.screens.searchDevices.SearchDevicesMainContent

val TABS = listOf(KnownDevicesTab, SearchDevicesTab)

object KnownDevicesTab : Tab {
    override val options: TabOptions

        @Composable
        get() {
            val title = stringResource(Res.string.favorites_tab_title)
            val icon = rememberVectorPainter(Icons.Default.Favorite)

            return remember {
                TabOptions(
                    index = 0u,
                    title = title,
                    icon = icon
                )
            }
        }

    @Composable
    override fun Content() {
        KnownDevicesMainScreen()
    }
}

object SearchDevicesTab : Tab {
    override val options: TabOptions

    @Composable
    get() {
        val title = stringResource(Res.string.scan_tab_title)
        val icon = rememberVectorPainter(Icons.Default.Search)
        return remember {
            TabOptions(
                index = 1u,
                title = title,
                icon = icon
            )
        }
    }

    @Composable
    override fun Content() {
        SearchDevicesMainContent()
    }
}