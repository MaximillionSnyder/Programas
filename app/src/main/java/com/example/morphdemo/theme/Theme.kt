package com.example.morphdemo.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4C4DDC),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F4FB),
    onBackground = Color(0xFF14121F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14121F),
    surfaceVariant = Color(0xFFE8E6F4),
    onSurfaceVariant = Color(0xFF5A5770),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB9B6FF),
    onPrimary = Color(0xFF1B1A2E),
    background = Color(0xFF0F0E16),
    onBackground = Color(0xFFF2F0FA),
    surface = Color(0xFF1A1826),
    onSurface = Color(0xFFF2F0FA),
    surfaceVariant = Color(0xFF2B2939),
    onSurfaceVariant = Color(0xFFB2AEC6),
)

@Composable
fun MorphDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
