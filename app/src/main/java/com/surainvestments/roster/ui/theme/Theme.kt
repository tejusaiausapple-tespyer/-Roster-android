package com.surainvestments.roster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BrandIndigoLight,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = AccentEmeraldLight,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = WarningAmberLight,
    onTertiary = Color.White,
    error = ErrorRedLight,
    onError = Color.White,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = BackgroundLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SeparatorLight,
    surfaceContainerHighest = OutlineLight.copy(alpha = 0.35f),
    outline = OutlineLight,
    outlineVariant = SeparatorLight,
    inverseSurface = TextPrimaryLight,
    inverseOnSurface = SurfaceLight,
    inversePrimary = BrandIndigoDark,
)

private val DarkColors = darkColorScheme(
    primary = BrandIndigoDark,
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = AccentEmeraldDark,
    onSecondary = Color(0xFF022C22),
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = WarningAmberDark,
    onTertiary = Color(0xFF451A03),
    error = ErrorRedDark,
    onError = Color(0xFF450A0A),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SeparatorDark,
    surfaceContainerHighest = OutlineDark,
    outline = OutlineDark,
    outlineVariant = SeparatorDark,
    inverseSurface = TextPrimaryDark,
    inverseOnSurface = BackgroundDark,
    inversePrimary = BrandIndigoLight,
)

/**
 * Material 3 Expressive theme with brand-locked indigo (not wallpaper-driven
 * Material You) so Rosterra reads the same across devices — matching iOS/PWA.
 * Expressive motion stays on for springy, premium transitions app-wide.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RosterraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        typography = RosterraTypography,
        shapes = RosterraShapes,
        content = content,
    )
}
