package com.advancedplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.advancedplayer.data.ThemeMode

data class AccentOption(val argb: Long, val name: String)

val AccentPresets = listOf(
    AccentOption(0xFF1DB954, "Green"),
    AccentOption(0xFFE53935, "Red"),
    AccentOption(0xFF2E77D0, "Blue"),
    AccentOption(0xFF8E44EC, "Purple"),
    AccentOption(0xFFE91E8C, "Pink"),
    AccentOption(0xFFFF7A1A, "Orange"),
    AccentOption(0xFFF5C518, "Yellow"),
    AccentOption(0xFF1ABC9C, "Teal"),
    AccentOption(0xFFFFFFFF, "White"),
    AccentOption(0xFF000000, "Black"),
)

/** Extra palette values Material's scheme has no slot for. */
data class PlayerColors(
    val accent: Color,
    val onAccent: Color,
    val muted: Color,
    val card: Color,
    val cardHover: Color,
    val isDark: Boolean,
)

val LocalPlayerColors = staticCompositionLocalOf {
    PlayerColors(Color(0xFF1DB954), Color.Black, Color(0xFFB3B3B3), Color(0xFF181818), Color(0xFF282828), true)
}

private val Dark = darkColorScheme(
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF242424),
    surfaceContainer = Color(0xFF181818),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2E2E2E),
    surfaceContainerLow = Color(0xFF161616),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB3B3B3),
    outline = Color(0xFF3E3E3E),
    outlineVariant = Color(0xFF2A2A2A),
)

private val Light = lightColorScheme(
    background = Color(0xFFF7F7F5),
    surface = Color(0xFFF7F7F5),
    surfaceVariant = Color(0xFFEAEAE6),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFEFEB),
    surfaceContainerHighest = Color(0xFFE4E4E0),
    surfaceContainerLow = Color(0xFFF2F2EE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111111),
    onSurface = Color(0xFF111111),
    onSurfaceVariant = Color(0xFF5E5E5A),
    outline = Color(0xFFCFCFCA),
    outlineVariant = Color(0xFFDDDDD8),
)

/**
 * Only for colours extracted from artwork (never the user's chosen accent, which is used
 * exactly as picked): a near-black cover on the dark theme is lifted slightly so the
 * player controls stay visible.
 */
fun usableAccent(accent: Color, dark: Boolean): Color {
    val l = accent.luminance()
    return when {
        dark && l < 0.03f -> lerp(accent, Color.White, 0.35f)
        !dark && l > 0.85f -> lerp(accent, Color.Black, 0.35f)
        else -> accent
    }
}

fun contentOn(color: Color): Color = if (color.luminance() > 0.45f) Color(0xFF0A0A0A) else Color.White

val MonoFamily = FontFamily.Monospace

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    )
}

val MonoLabel = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, letterSpacing = 0.5.sp)

@Composable
fun AdvancedPlayerTheme(themeMode: ThemeMode, accentArgb: Long, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val accent = Color(accentArgb)
    val onAccent = contentOn(accent)
    val base = if (dark) Dark else Light
    val scheme: ColorScheme = base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = lerp(base.surfaceVariant, accent, 0.25f),
        onPrimaryContainer = base.onSurface,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = lerp(base.surfaceVariant, accent, 0.18f),
        onSecondaryContainer = base.onSurface,
        tertiary = accent,
        surfaceTint = Color.Transparent,
    )
    val colors = PlayerColors(
        accent = accent,
        onAccent = onAccent,
        muted = base.onSurfaceVariant,
        card = if (dark) Color(0xFF1C1C1C) else Color(0xFFFFFFFF),
        cardHover = if (dark) Color(0xFF2A2A2A) else Color(0xFFEDEDE9),
        isDark = dark,
    )
    CompositionLocalProvider(LocalPlayerColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
