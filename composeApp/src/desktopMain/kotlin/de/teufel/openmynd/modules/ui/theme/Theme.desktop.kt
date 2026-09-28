package de.teufel.openmynd.modules.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
internal actual fun SystemAppearance(isDark: Boolean) = Unit

@Composable
internal actual fun systemPrefersDarkTheme(): Boolean {
    val linuxDark by LinuxTheme.isDark.collectAsState()
    return linuxDark ?: isSystemInDarkTheme()
}
