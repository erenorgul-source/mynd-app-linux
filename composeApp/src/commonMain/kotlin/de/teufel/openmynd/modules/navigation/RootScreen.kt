package de.teufel.openmynd.modules.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.TabDisposable
import cafe.adriel.voyager.navigator.tab.TabNavigator

object RootScreen : Screen {
    @Composable
    override fun Content() {
        TabNavigator(
            KnownDevicesTab,
            tabDisposable = {
                TabDisposable(
                    navigator = it,
                    tabs = TABS
                )
            }
        ) { tabNavigator ->
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    BottomNavigationBar(
                        modifier = Modifier
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    CurrentTab()
                }
            }
        }
    }
} 