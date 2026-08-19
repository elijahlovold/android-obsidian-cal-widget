package com.example.android_home_cal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import com.example.android_home_cal.R

@Composable
fun AndroidhomecalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val background = colorResource(R.color.config_background)
    val surface = colorResource(R.color.config_surface)
    val onSurface = colorResource(R.color.config_on_surface)
    val accent = colorResource(R.color.widget_accent)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accent,
            background = background,
            surface = surface,
            onBackground = onSurface,
            onSurface = onSurface
        )
    } else {
        lightColorScheme(
            primary = accent,
            background = background,
            surface = surface,
            onBackground = onSurface,
            onSurface = onSurface
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
