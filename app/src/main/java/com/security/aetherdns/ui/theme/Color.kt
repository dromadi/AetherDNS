package com.security.aetherdns.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Palet brand (dipakai skema warna non-dinamis)
internal val Navy900 = Color(0xFF0B2E6F)
internal val Blue600 = Color(0xFF1E5BD8)
internal val Blue200 = Color(0xFFADC6FF)
internal val Teal600 = Color(0xFF00897B)
internal val Teal200 = Color(0xFF80CBC4)

internal val DarkBackground = Color(0xFF0F1115)
internal val DarkSurface = Color(0xFF171A20)
internal val DarkSurfaceVariant = Color(0xFF242833)
internal val LightBackground = Color(0xFFF6F8FC)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightSurfaceVariant = Color(0xFFE9EDF5)

/** Warna semantik status (aman / peringatan / bahaya) yang menyesuaikan tema terang dan gelap. */
@Immutable
data class StatusColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val error: Color,
    val errorContainer: Color
)

internal val LightStatusColors = StatusColors(
    success = Color(0xFF1B7F3B),
    successContainer = Color(0xFFDDF5E3),
    warning = Color(0xFFB45309),
    warningContainer = Color(0xFFFFF1DB),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFFE4E4)
)

internal val DarkStatusColors = StatusColors(
    success = Color(0xFF6FDC8C),
    successContainer = Color(0xFF173A24),
    warning = Color(0xFFFFB866),
    warningContainer = Color(0xFF3D2A12),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF451C1C)
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

// Akses ringkas untuk komponen; selalu mengikuti tema aktif (termasuk Material You).
val PrimaryBlue: Color
    @Composable @ReadOnlyComposable get() = androidx.compose.material3.MaterialTheme.colorScheme.primary
val SuccessGreen: Color
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current.success
val SuccessContainer: Color
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current.successContainer
val WarningOrange: Color
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current.warning
val WarningOrangeLight: Color
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current.warningContainer
val ErrorRed: Color
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current.error
val ErrorContainer: Color
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current.errorContainer
