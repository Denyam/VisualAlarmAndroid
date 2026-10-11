package com.example.visualalarm.ui.theme

import androidx.compose.material3.MaterialTheme as M3MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun VisualAlarmTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    M3MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}