package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ScannerBlueDarkPrimary,
    onPrimary = ScannerBlueDarkOnPrimary,
    primaryContainer = ScannerBlueDarkPrimaryContainer,
    onPrimaryContainer = ScannerBlueDarkOnPrimaryContainer,
    background = ScannerDarkBackground,
    surface = ScannerDarkSurface,
    surfaceVariant = ScannerDarkSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = ScannerBluePrimary,
    onPrimary = ScannerBlueOnPrimary,
    primaryContainer = ScannerBluePrimaryContainer,
    onPrimaryContainer = ScannerBlueOnPrimaryContainer,
    secondary = ScannerSecondary,
    secondaryContainer = ScannerSecondaryContainer,
    onSecondaryContainer = ScannerOnSecondaryContainer,
    background = ScannerBackgroundLight,
    surface = ScannerSurfaceLight,
    surfaceVariant = ScannerSurfaceVariantLight
)

@Composable
fun PaperScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent scanner brand blue styling
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    PaperScanTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
