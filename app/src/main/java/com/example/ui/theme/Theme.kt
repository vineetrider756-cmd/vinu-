package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val StudioColorScheme = darkColorScheme(
    primary = StudioCyan,
    onPrimary = SpaceBlack,
    primaryContainer = SlateLight,
    onPrimaryContainer = SoftWhite,
    secondary = StudioTeal,
    onSecondary = SpaceBlack,
    secondaryContainer = SlateMedium,
    onSecondaryContainer = SoftWhite,
    tertiary = HotPink,
    onTertiary = SoftWhite,
    background = SpaceBlack,
    onBackground = SoftWhite,
    surface = CharcoalDark,
    onSurface = SoftWhite,
    surfaceVariant = SlateMedium,
    onSurfaceVariant = MutedGrey,
    outline = BorderGrey
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark Theme for visual/photographic editing clarity
    content: @Composable () -> Unit
) {
    val colorScheme = StudioColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
