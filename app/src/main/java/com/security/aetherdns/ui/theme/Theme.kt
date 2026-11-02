package com.security.aetherdns.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Skema lengkap dari palet brand agar tombol, tautan, navigasi, dan kartu status bergradien terlihat satu keluarga.
private val DarkColorScheme = darkColorScheme(
    primary = Blue200,
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF17428F),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFF8FD3E6),
    onSecondary = Color(0xFF003642),
    secondaryContainer = Color(0xFF203A63),
    onSecondaryContainer = Color(0xFFD9E2FF),
    tertiary = Teal200,
    background = DarkBackground,
    onBackground = Color(0xFFE2E6EE),
    surface = DarkBackground,
    onSurface = Color(0xFFE2E6EE),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFA9B2C3),
    surfaceContainerLowest = Color(0xFF0A0D12),
    surfaceContainerLow = Color(0xFF12161D),
    surfaceContainer = Color(0xFF141920),
    surfaceContainerHigh = DarkSurface,
    surfaceContainerHighest = DarkSurfaceVariant,
    outline = Color(0xFF5A6476),
    outlineVariant = Color(0xFF2A3240)
)

private val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Navy900,
    secondary = Color(0xFF1A7FA0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6FF),
    onSecondaryContainer = Navy900,
    tertiary = Teal600,
    background = LightBackground,
    onBackground = Color(0xFF151A23),
    surface = LightBackground,
    onSurface = Color(0xFF151A23),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF545E70),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9FD),
    surfaceContainer = Color(0xFFEDF1F8),
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = LightSurfaceVariant,
    outline = Color(0xFF8A94A6),
    outlineVariant = Color(0xFFD6DDE9)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun AetherDnsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Warna brand secara bawaan: Material You membuat tombol mengikuti wallpaper, tidak serasi dengan kartu status
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
