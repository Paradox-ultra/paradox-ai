package com.phoneagent.ai.ui.theme

import androidx.compose.ui.graphics.Color
import com.phoneagent.ai.data.model.AccentColor

data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceHighlight: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val userBubble: Color,
    val assistantBubble: Color,
    val accent: Color,
    val onAccent: Color,
    val error: Color,
    val success: Color,
    val warning: Color
)

fun getAccentColorValue(accent: AccentColor): Color {
    return Color(accent.hex)
}

fun getAccentOnColor(accent: AccentColor): Color {
    return when (accent) {
        AccentColor.CYAN -> Color(0xFF0D1B1E)
        else -> Color(0xFFFFFFFF)
    }
}

val DarkAppColors = { accent: AccentColor ->
    val acc = getAccentColorValue(accent)
    val onAcc = getAccentOnColor(accent)
    AppColors(
        background = Color(0xFF111215),
        surface = Color(0xFF181A20),
        surfaceVariant = Color(0xFF22252E),
        surfaceHighlight = Color(0xFF2D323E),
        border = Color(0xFF282C37),
        textPrimary = Color(0xFFF3F4F6),
        textSecondary = Color(0xFF9CA3AF),
        textMuted = Color(0xFF6B7280),
        userBubble = Color(0xFF242936),
        assistantBubble = Color(0xFF181A20),
        accent = acc,
        onAccent = onAcc,
        error = Color(0xFFEF4444),
        success = Color(0xFF10B981),
        warning = Color(0xFFF59E0B)
    )
}

val LightAppColors = { accent: AccentColor ->
    val acc = getAccentColorValue(accent)
    val onAcc = getAccentOnColor(accent)
    AppColors(
        background = Color(0xFFF7F8FA),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEDF0F5),
        surfaceHighlight = Color(0xFFE2E6EC),
        border = Color(0xFFE2E5EA),
        textPrimary = Color(0xFF191C21),
        textSecondary = Color(0xFF5A6270),
        textMuted = Color(0xFF9098A5),
        userBubble = Color(0xFFE4E9F2),
        assistantBubble = Color(0xFFFFFFFF),
        accent = acc,
        onAccent = onAcc,
        error = Color(0xFFDC2626),
        success = Color(0xFF059669),
        warning = Color(0xFFD97706)
    )
}

val AmoledAppColors = { accent: AccentColor ->
    val acc = getAccentColorValue(accent)
    val onAcc = getAccentOnColor(accent)
    AppColors(
        background = Color(0xFF000000),
        surface = Color(0xFF080808),
        surfaceVariant = Color(0xFF121212),
        surfaceHighlight = Color(0xFF1E1E1E),
        border = Color(0xFF1C1C1C),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFA3A3A3),
        textMuted = Color(0xFF666666),
        userBubble = Color(0xFF161618),
        assistantBubble = Color(0xFF080808),
        accent = acc,
        onAccent = onAcc,
        error = Color(0xFFFF4D4D),
        success = Color(0xFF00E676),
        warning = Color(0xFFFFB300)
    )
}
