package com.example.iudigitalradio_trabajofinal_camilomendez.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = IUTechCyan,
    onPrimary = IUBackground,
    primaryContainer = IUNavyPrimary,
    onPrimaryContainer = IUTextPrimary,
    secondary = IUGoldAccent,
    onSecondary = IUNavyDark,
    tertiary = IURedAccent,
    background = IUBackground,
    surface = IUSurface,
    surfaceVariant = IUSurfaceCard,
    outline = IUBorderSubtle
)

@Composable
fun IUDigitalRadioTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = IUNavyDark.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
