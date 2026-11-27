package com.yuedu.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.yuedu.reader.model.AppAppearance

data class LiquidGlassPalette(
    val isDark: Boolean,
    val baseBackground: Color,
    val glassSurface: Color,
    val glassSurfaceStrong: Color,
    val glassHighlightTop: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentSoft: Color,
    val orbPrimary: Color,
    val orbSecondary: Color,
    val orbTertiary: Color,
    val pillIndicator: Color,
    val danger: Color,
    val success: Color
)

private val LightGlassPalette = LiquidGlassPalette(
    isDark = false,
    baseBackground = Color(0xFFF2F5FA),
    glassSurface = Color(0xBAFFFFFF),
    glassSurfaceStrong = Color(0xE0FFFFFF),
    glassHighlightTop = Color(0x99FFFFFF),
    glassBorderTop = Color(0xE6FFFFFF),
    glassBorderBottom = Color(0x38CBD5E1),
    textPrimary = Color(0xFF14161A),
    textSecondary = Color(0xFF636873),
    accent = Color(0xFF007AFF),
    accentSoft = Color(0x1F007AFF),
    orbPrimary = Color(0x5538BDF8),
    orbSecondary = Color(0x4D818CF8),
    orbTertiary = Color(0x45FB7185),
    pillIndicator = Color(0xEAFFFFFF),
    danger = Color(0xFFFF3B30),
    success = Color(0xFF28CD41)
)

private val DarkGlassPalette = LiquidGlassPalette(
    isDark = true,
    baseBackground = Color(0xFF0A0C12),
    glassSurface = Color(0xA61A1D26),
    glassSurfaceStrong = Color(0xD920242F),
    glassHighlightTop = Color(0x28FFFFFF),
    glassBorderTop = Color(0x42FFFFFF),
    glassBorderBottom = Color(0x14FFFFFF),
    textPrimary = Color(0xFFF5F7FA),
    textSecondary = Color(0xFF98A0B0),
    accent = Color(0xFF47A1FF),
    accentSoft = Color(0x2647A1FF),
    orbPrimary = Color(0x471D4ED8),
    orbSecondary = Color(0x476D28D9),
    orbTertiary = Color(0x380D9488),
    pillIndicator = Color(0x36FFFFFF),
    danger = Color(0xFFFF453A),
    success = Color(0xFF32D74B)
)

val LocalLiquidGlass = staticCompositionLocalOf { LightGlassPalette }

val BookCoverPalettes: List<Pair<Color, Color>> = listOf(
    Color(0xFF3B82F6) to Color(0xFF1D4ED8),
    Color(0xFFEC4899) to Color(0xFFBE185D),
    Color(0xFF10B981) to Color(0xFF047857),
    Color(0xFFF59E0B) to Color(0xFFD97706),
    Color(0xFF8B5CF6) to Color(0xFF5B21B6),
    Color(0xFF06B6D4) to Color(0xFF0E7490)
)

private val YueDuTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )
)

@Composable
fun YueDuTheme(
    appearance: AppAppearance,
    content: @Composable () -> Unit
) {
    val isDark = when (appearance) {
        AppAppearance.SYSTEM -> isSystemInDarkTheme()
        AppAppearance.LIGHT -> false
        AppAppearance.DARK -> true
    }

    val palette = if (isDark) DarkGlassPalette else LightGlassPalette
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.accent,
            background = palette.baseBackground,
            surface = palette.glassSurfaceStrong,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            background = palette.baseBackground,
            surface = palette.glassSurfaceStrong,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary
        )
    }

    CompositionLocalProvider(LocalLiquidGlass provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = YueDuTypography,
            content = content
        )
    }
}
