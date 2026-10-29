package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.models.AppThemeMode

private val ForgeDarkColorScheme = darkColorScheme(
    primary = CopperLight,
    onPrimary = Color(0xFF291400),
    primaryContainer = CopperContainerDark,
    onPrimaryContainer = OnCopperContainerDark,
    secondary = Color(0xFFDDBFA8),
    onSecondary = Color(0xFF3E2B1B),
    secondaryContainer = Color(0xFF564130),
    onSecondaryContainer = Color(0xFFFBDAC2),
    tertiary = Color(0xFFBDC998),
    onTertiary = Color(0xFF28330F),
    tertiaryContainer = Color(0xFF3E4A23),
    onTertiaryContainer = Color(0xFFD9E5B2),
    background = ObsidianBackground,
    onBackground = WarmSandOnSurface,
    surface = ObsidianSurface,
    onSurface = WarmSandOnSurface,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = WarmSandMuted,
    surfaceContainerLowest = Color(0xFF090806),
    surfaceContainerLow = Color(0xFF14110E),
    surfaceContainer = Color(0xFF1B1714),
    surfaceContainerHigh = ObsidianSurfaceHigh,
    surfaceContainerHighest = Color(0xFF362E27),
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    error = StatusErrorRed,
    onError = Color(0xFF490005),
    errorContainer = Color(0xFF69000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val ForgeLightColorScheme = lightColorScheme(
    primary = CopperSeed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC1),
    onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF735944),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF2A1707),
    tertiary = Color(0xFF59633A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE8B5),
    onTertiaryContainer = Color(0xFF171E01),
    background = ForgeLightBackground,
    onBackground = ForgeLightOnSurface,
    surface = ForgeLightSurface,
    onSurface = ForgeLightOnSurface,
    surfaceVariant = ForgeLightSurfaceVariant,
    onSurfaceVariant = ForgeLightOnSurfaceVariant,
    outline = Color(0xFF847469),
    outlineVariant = Color(0xFFD7C3B7)
)

val ForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun ForgeShellTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDark) ForgeDarkColorScheme else ForgeLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ForgeTypography,
        shapes = ForgeShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    ForgeShellTheme(
        themeMode = if (darkTheme) AppThemeMode.DARK else AppThemeMode.LIGHT,
        content = content
    )
}
