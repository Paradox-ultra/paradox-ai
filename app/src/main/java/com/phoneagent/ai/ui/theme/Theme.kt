package com.phoneagent.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.phoneagent.ai.data.model.AccentColor
import com.phoneagent.ai.data.model.ThemeMode

val LocalAppColors = staticCompositionLocalOf<AppColors> {
    error("No AppColors provided")
}

@Composable
fun PhoneAgentTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentColor: AccentColor = AccentColor.CYAN,
    content: @Composable () -> Unit
) {
    val colors = when (themeMode) {
        ThemeMode.DARK -> DarkAppColors(accentColor)
        ThemeMode.LIGHT -> LightAppColors(accentColor)
        ThemeMode.AMOLED -> AmoledAppColors(accentColor)
    }

    val materialColors = if (themeMode == ThemeMode.LIGHT) {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            surface = colors.surface,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            error = colors.error
        )
    } else {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            surface = colors.surface,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            error = colors.error
        )
    }

    CompositionLocalProvider(
        LocalAppColors provides colors
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}
