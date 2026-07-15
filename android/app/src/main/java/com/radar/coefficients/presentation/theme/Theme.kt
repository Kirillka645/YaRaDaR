package com.radar.coefficients.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// High-contrast palette for driving — not Yandex Pro clone
val ZoneGreen = Color(0xFF1B8A4A)
val ZoneYellow = Color(0xFFE6A700)
val ZoneOrange = Color(0xFFE65C00)
val ZoneRed = Color(0xFFD32F2F)
val DemoAmber = Color(0xFFFFC107)
val RealTeal = Color(0xFF00897B)
val StaleGray = Color(0xFF9E9E9E)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0D47A1),
    onPrimary = Color.White,
    secondary = Color(0xFF00695C),
    onSecondary = Color.White,
    tertiary = Color(0xFFBF360C),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF102027),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF102027),
    error = Color(0xFFB00020)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D1B2A),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF00201C),
    tertiary = Color(0xFFFFAB91),
    background = Color(0xFF0B1218),
    onBackground = Color(0xFFE8EEF2),
    surface = Color(0xFF15202B),
    onSurface = Color(0xFFE8EEF2),
    error = Color(0xFFCF6679)
)

val TouchTargetMin = 56.dp

@Composable
fun RadarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}

fun coefficientColor(coefficient: Double): Color = when {
    coefficient >= 2.0 -> ZoneRed
    coefficient >= 1.5 -> ZoneOrange
    coefficient >= 1.1 -> ZoneYellow
    else -> ZoneGreen
}
