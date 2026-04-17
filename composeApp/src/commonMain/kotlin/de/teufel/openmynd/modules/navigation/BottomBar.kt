package de.teufel.openmynd.modules.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator

@Composable
fun BottomNavigationBar(modifier: Modifier = Modifier) {
    val tabNavigator = LocalTabNavigator.current
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
    ) {
        Spacer(modifier = Modifier.height(0.5.dp).fillMaxWidth().background(MaterialTheme.colorScheme.background))
        Spacer(modifier = Modifier.height(2.dp).fillMaxWidth().background(MaterialTheme.colorScheme.background))
        NavigationBar(
            modifier = modifier.height(60.dp),
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 1.dp
        ) {
            TABS.forEach { tab ->
                val selected = tabNavigator.current.key == tab.key
                NavigationRailItem(
                    icon = {
                        Icon(
                            painter = tab.options.icon!!,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            contentDescription = tab.options.title,
                            modifier = Modifier
                                .width(36.dp)
                                .height(36.dp)
                        )
                    },
                    alwaysShowLabel = false,
                    selected = false,
                    onClick = {
                        tabNavigator.current = tab
                    },
                    modifier = Modifier.weight(1F)
                )
            }
        }
    }

}