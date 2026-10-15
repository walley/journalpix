package com.journalpix.ui.theme

import android.app.Activity
nimport android.os.Build
nimport androidx.compose.foundation.isSystemInDarkTheme
nimport androidx.compose.material3.MaterialTheme
nimport androidx.compose.material3.darkColorScheme
nimport androidx.compose.material3.dynamicDarkColorScheme
nimport androidx.compose.material3.dynamicLightColorScheme
nimport androidx.compose.material3.lightColorScheme
nimport androidx.compose.runtime.Composable
nimport androidx.compose.runtime.SideEffect
nimport androidx.compose.ui.graphics.toArgb
nimport androidx.compose.ui.platform.LocalContext
nimport androidx.compose.ui.platform.LocalView
nimport androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun JournalPixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
