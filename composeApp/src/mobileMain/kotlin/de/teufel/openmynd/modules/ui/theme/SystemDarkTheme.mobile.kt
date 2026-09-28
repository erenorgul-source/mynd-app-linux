package de.teufel.openmynd.modules.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

@Composable
internal actual fun systemPrefersDarkTheme(): Boolean = isSystemInDarkTheme()
