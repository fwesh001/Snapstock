package com.example.snapstock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SnapStockGreen = Color(0xFF2E7D32)
val Amber = Color(0xFFFFB300)
val OffWhite = Color(0xFFF8F9FA)
val InfoBlue = Color(0xFF1A73E8)
val DangerRed = Color(0xFFD32F2F)

private val LightColorScheme = lightColorScheme(
    primary = SnapStockGreen,
    secondary = Amber,
    tertiary = InfoBlue,
    background = OffWhite,
    surface = OffWhite,
    error = DangerRed
)

private val DarkColorScheme = darkColorScheme(
    primary = SnapStockGreen,
    secondary = Amber,
    tertiary = InfoBlue,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    error = Color(0xFFCF6679)
)

@Composable
fun SnapStockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

