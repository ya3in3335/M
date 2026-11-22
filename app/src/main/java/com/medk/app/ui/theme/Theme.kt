package com.medk.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.medk.app.R

object MedkColors {
    val Black = Color(0xFF080808)
    val Surface = Color(0xFF121118)
    val SurfaceHigh = Color(0xFF1A1823)
    val Stroke = Color(0xFF2A2735)
    val Purple = Color(0xFF762BDE)
    val Violet = Color(0xFFA64BFF)
    val White = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA5A0AE)
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Danger = Color(0xFFEF4444)
    val Info = Color(0xFF3B82F6)

    val PrimaryGradient = Brush.horizontalGradient(listOf(Violet, Purple))
}

/** Per-category palette (gradient start/end + accent). */
@Immutable
data class CategoryPalette(val start: Color, val end: Color, val accent: Color) {
    val brush get() = Brush.linearGradient(listOf(start, end))
    val horizontal get() = Brush.horizontalGradient(listOf(end, start))
}

fun categoryPalette(key: String?): CategoryPalette = when (key) {
    "orange", "gaming" -> CategoryPalette(Color(0xFFFF9A1F), Color(0xFFB4400B), Color(0xFFFFB547))
    "blue", "subscriptions" -> CategoryPalette(Color(0xFF2F6BFF), Color(0xFF13286E), Color(0xFF6EA0FF))
    "purple", "accounts" -> CategoryPalette(Color(0xFF9B4DFF), Color(0xFF41167F), Color(0xFFC08BFF))
    "green", "p2p" -> CategoryPalette(Color(0xFF22C55E), Color(0xFF0B5A33), Color(0xFF6EE7A0))
    "cyan", "other" -> CategoryPalette(Color(0xFF14B8E6), Color(0xFF173E8F), Color(0xFF67D8FF))
    else -> CategoryPalette(MedkColors.Violet, MedkColors.Purple, MedkColors.Violet)
}

val Cairo = FontFamily(
    Font(R.font.cairo_400, FontWeight.Normal),
    Font(R.font.cairo_600, FontWeight.SemiBold),
    Font(R.font.cairo_700, FontWeight.Bold),
    Font(R.font.cairo_800, FontWeight.ExtraBold),
)

private fun s(size: Int, weight: FontWeight, line: Int = size + 8) =
    TextStyle(fontFamily = Cairo, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp)

private val MedkTypography = Typography(
    displayLarge = s(30, FontWeight.ExtraBold, 38),
    displayMedium = s(26, FontWeight.ExtraBold, 34),
    displaySmall = s(22, FontWeight.ExtraBold, 30),
    headlineLarge = s(20, FontWeight.Bold, 28),
    headlineMedium = s(18, FontWeight.Bold, 26),
    headlineSmall = s(17, FontWeight.Bold, 24),
    titleLarge = s(15, FontWeight.Bold, 22),
    titleMedium = s(14, FontWeight.Bold, 20),
    titleSmall = s(13, FontWeight.SemiBold, 19),
    bodyLarge = s(14, FontWeight.Normal, 21),
    bodyMedium = s(13, FontWeight.Normal, 20),
    bodySmall = s(11, FontWeight.Normal, 17),
    labelLarge = s(13, FontWeight.Bold, 18),
    labelMedium = s(11, FontWeight.SemiBold, 16),
    labelSmall = s(10, FontWeight.SemiBold, 14),
)

private val DarkScheme = darkColorScheme(
    primary = MedkColors.Purple, onPrimary = Color.White,
    primaryContainer = Color(0xFF2A1450), onPrimaryContainer = Color(0xFFE9D8FF),
    secondary = MedkColors.Violet, onSecondary = Color.White,
    background = MedkColors.Black, onBackground = Color.White,
    surface = MedkColors.Black, onSurface = Color.White,
    surfaceVariant = MedkColors.Surface, onSurfaceVariant = MedkColors.TextSecondary,
    surfaceContainerLowest = MedkColors.Black, surfaceContainerLow = Color(0xFF100E15),
    surfaceContainer = MedkColors.Surface, surfaceContainerHigh = MedkColors.SurfaceHigh,
    surfaceContainerHighest = Color(0xFF262233),
    outline = MedkColors.Stroke, outlineVariant = Color(0xFF221F2C),
    error = MedkColors.Danger, onError = Color.White,
)

private val LightScheme = lightColorScheme(
    primary = MedkColors.Purple, onPrimary = Color.White,
    primaryContainer = Color(0xFFEFE3FF), onPrimaryContainer = Color(0xFF2A0A5E),
    secondary = MedkColors.Violet, onSecondary = Color.White,
    background = Color(0xFFF6F4FA), onBackground = Color(0xFF14121A),
    surface = Color(0xFFF6F4FA), onSurface = Color(0xFF14121A),
    surfaceVariant = Color.White, onSurfaceVariant = Color(0xFF6B6577),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1EEF6),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF0ECF7),
    surfaceContainerHighest = Color(0xFFE7E2F0),
    outline = Color(0xFFDDD6E8), outlineVariant = Color(0xFFEAE5F2),
    error = MedkColors.Danger, onError = Color.White,
)

val LocalIsDark = staticCompositionLocalOf { true }

@Composable
fun MedkTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, typography = MedkTypography) {
        CompositionLocalProvider(
            LocalContentColor provides scheme.onBackground,
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalIsDark provides dark,
            content = content,
        )
    }
}
