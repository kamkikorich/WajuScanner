package com.example.wajuscanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palet terang — identiti jenama tetap walaupun wallpaper sistem berubah
// (dynamic color dimatikan supaya aplikasi tidak "jawab ikut tema telefon"
// lalu jadikan gelap/ungu tiada akar).
private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = TealOnPrimaryContainer,
    secondary = AquaSecondary,
    secondaryContainer = AquaSecondaryContainer,
    onSecondaryContainer = AquaOnSecondaryContainer,
    background = InkBackground,
    onBackground = InkOnSurface,
    surface = InkSurface,
    onSurface = InkOnSurface,
    surfaceVariant = InkSurfaceVariant,
    onSurfaceVariant = InkOnSurfaceVariant,
    outline = InkOutline,
)

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealPrimaryContainerDark,
    onPrimaryContainer = TealOnPrimaryContainerDark,
    background = InkSurfaceDark,
    onBackground = InkOnSurfaceDark,
    surface = InkSurfaceDark,
    onSurface = InkOnSurfaceDark,
    surfaceVariant = InkSurfaceVariantDark,
    onSurfaceVariant = InkOnSurfaceVariantDark,
    outline = InkOutlineDark,
)

@Composable
fun WajuScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color dimatikan — identiti terang aplikasi adalah sebahagian
    // daripada reka bentuk (Stitch Redesign 2026).
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
