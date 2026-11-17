package com.codeforge.agent.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF89B4FA),
    secondary = androidx.compose.ui.graphics.Color(0xFFA6E3A1),
    tertiary = androidx.compose.ui.graphics.Color(0xFFF9E2AF),
    background = androidx.compose.ui.graphics.Color(0xFF1E1E2E),
    surface = androidx.compose.ui.graphics.Color(0xFF313244),
    error = androidx.compose.ui.graphics.Color(0xFFF38BA8)
)

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF1E66F5),
    secondary = androidx.compose.ui.graphics.Color(0xFF40A02B),
    tertiary = androidx.compose.ui.graphics.Color(0xFFDF8E1D),
    background = androidx.compose.ui.graphics.Color(0xFFEFF1F5),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    error = androidx.compose.ui.graphics.Color(0xFFD20F39)
)

@Composable
fun CodeForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}