package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.domain.model.ThemeMode

private val NotebookDarkColorScheme = darkColorScheme(
    primary = ElectricBlue400,
    onPrimary = DeepNavy950,
    primaryContainer = DeepNavy800,
    onPrimaryContainer = IceBlue100,
    secondary = CyanAccent,
    onSecondary = DeepNavy950,
    secondaryContainer = NightCardElevated,
    onSecondaryContainer = IceBlue200,
    tertiary = EmeraldVault,
    onTertiary = DeepNavy950,
    tertiaryContainer = EmeraldVaultContainerDark,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = DeepNavy950,
    onBackground = InkLight,
    surface = DeepNavy900,
    onSurface = InkLight,
    surfaceVariant = NightCard,
    onSurfaceVariant = InkMutedDark,
    surfaceContainerLowest = DeepNavy950,
    surfaceContainerLow = DeepNavy900,
    surfaceContainer = NightCard,
    surfaceContainerHigh = NightCardElevated,
    surfaceContainerHighest = Color(0xFF1B3057),
    outline = NightOutline,
    outlineVariant = Color(0xFF18294A),
    error = RoseAlert,
    onError = Color.White,
    errorContainer = RoseAlertContainerDark,
    onErrorContainer = Color(0xFFFECACA)
)

private val NotebookLightColorScheme = lightColorScheme(
    primary = DeepNavy800,
    onPrimary = Color.White,
    primaryContainer = IceBlue100,
    onPrimaryContainer = DeepNavy900,
    secondary = Cobalt500,
    onSecondary = Color.White,
    secondaryContainer = IceBlue50,
    onSecondaryContainer = DeepNavy800,
    tertiary = EmeraldVault,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldVaultContainerLight,
    onTertiaryContainer = Color(0xFF065F46),
    background = PaperWhite,
    onBackground = InkDark,
    surface = PaperWhite,
    onSurface = InkDark,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = InkMutedLight,
    surfaceContainerLowest = PaperCard,
    surfaceContainerLow = Color(0xFFF3F7FC),
    surfaceContainer = PaperCard,
    surfaceContainerHigh = PaperSurfaceVariant,
    surfaceContainerHighest = Color(0xFFE0E9F5),
    outline = SlateOutlineLight,
    outlineVariant = Color(0xFFE4ECF7),
    error = RoseAlert,
    onError = Color.White,
    errorContainer = RoseAlertContainerLight,
    onErrorContainer = Color(0xFF991B1B)
)

val NotebookShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NotebookTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = if (darkTheme) NotebookDarkColorScheme else NotebookLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NotebookTypography,
        shapes = NotebookShapes,
        content = content
    )
}
