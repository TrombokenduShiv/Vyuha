package com.vyuha.demo.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Brand Colors ────────────────────────────────────────────────────
// DemoBank brand: Deep navy blue + teal accent

private val Navy900 = Color(0xFF0D1B2A)
private val Navy800 = Color(0xFF1B2838)
private val Navy700 = Color(0xFF1B3A4B)
private val Teal500 = Color(0xFF00B4D8)
private val Teal400 = Color(0xFF48CAE4)
private val Teal300 = Color(0xFF90E0EF)
private val Mint100 = Color(0xFFCAF0F8)
private val White = Color(0xFFFFFFFF)
private val OffWhite = Color(0xFFF8F9FA)
private val Gray200 = Color(0xFFE9ECEF)
private val Gray400 = Color(0xFFADB5BD)
private val Gray600 = Color(0xFF6C757D)
private val Gray800 = Color(0xFF343A40)
private val Red500 = Color(0xFFEF476F)
private val Red100 = Color(0xFFFDE8EF)
private val Green500 = Color(0xFF06D6A0)
private val Green100 = Color(0xFFE6FAF3)
private val Amber500 = Color(0xFFFFD166)

private val DemoBankLightColors = lightColorScheme(
    primary = Navy800,
    onPrimary = White,
    primaryContainer = Teal300,
    onPrimaryContainer = Navy900,
    secondary = Teal500,
    onSecondary = White,
    secondaryContainer = Mint100,
    onSecondaryContainer = Navy700,
    tertiary = Green500,
    onTertiary = White,
    background = OffWhite,
    onBackground = Navy900,
    surface = White,
    onSurface = Navy900,
    surfaceVariant = Gray200,
    onSurfaceVariant = Gray600,
    error = Red500,
    onError = White,
    errorContainer = Red100,
    onErrorContainer = Navy900,
    outline = Gray400
)

private val DemoBankDarkColors = darkColorScheme(
    primary = Teal400,
    onPrimary = Navy900,
    primaryContainer = Navy700,
    onPrimaryContainer = Teal300,
    secondary = Teal300,
    onSecondary = Navy900,
    secondaryContainer = Navy700,
    onSecondaryContainer = Mint100,
    tertiary = Green500,
    onTertiary = Navy900,
    background = Navy900,
    onBackground = OffWhite,
    surface = Navy800,
    onSurface = OffWhite,
    surfaceVariant = Navy700,
    onSurfaceVariant = Gray400,
    error = Red500,
    onError = White,
    errorContainer = Color(0xFF442336),
    onErrorContainer = Red100,
    outline = Gray600
)

@Composable
fun DemoBankTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DemoBankDarkColors else DemoBankLightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
