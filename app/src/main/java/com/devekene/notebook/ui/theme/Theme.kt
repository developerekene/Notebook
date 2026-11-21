package com.devekene.notebook.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val NotebookLightColorScheme = lightColorScheme(
    primary = DarkBlue900,
    onPrimary = Color.White,
    primaryContainer = SkyTint100,
    onPrimaryContainer = DarkBlue950,
    secondary = Cobalt500,
    onSecondary = Color.White,
    secondaryContainer = SkyTint50,
    onSecondaryContainer = DarkBlue900,
    tertiary = EmeraldVault,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldTint,
    onTertiaryContainer = Color(0xFF065F46),
    background = PorcelainBg,
    onBackground = InkPrimary,
    surface = PorcelainBg,
    onSurface = InkPrimary,
    surfaceVariant = SoftBlueTint,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = CrispWhite,
    surfaceContainerLow = Color(0xFFF2F6FC),
    surfaceContainer = CrispWhite,
    surfaceContainerHigh = SoftBlueTint,
    surfaceContainerHighest = ElevatedBlueTint,
    outline = HairlineBlue,
    outlineVariant = HairlineSubtle,
    error = RoseAlert,
    onError = Color.White,
    errorContainer = RoseTint,
    onErrorContainer = Color(0xFF991B1B)
)

val NotebookShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NotebookTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = NotebookLightColorScheme,
        typography = NotebookTypography,
        shapes = NotebookShapes,
        content = content
    )
}
