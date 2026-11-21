package dev.jayesh.tokenpulse.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object TP {
    val bg = Color(0xFF0F0E0D)
    val surface = Color(0xFF1A1816)
    val surfaceHi = Color(0xFF24211E)
    val border = Color(0x1AF3EEE8)
    val text = Color(0xFFF3EEE8)
    val muted = Color(0xFF9A928A)
    val faint = Color(0xFF5E5852)
    val accent = Color(0xFFE07A52)
    val accentHi = Color(0xFFFFA878)
    val amber = Color(0xFFF2B35B)
    val amberHi = Color(0xFFFFD892)
    val sage = Color(0xFF8FBF9A)
    val sky = Color(0xFF86A9E8)
    val danger = Color(0xFFFF5E57)
    val track = Color(0x1FF3EEE8)

    fun forPct(pct: Double, base: Color, hi: Color): Pair<Color, Color> =
        if (pct >= 90) danger to Color(0xFFFF9A8F) else base to hi
}

private val numbers = TextStyle(fontFeatureSettings = "tnum")

private val type = Typography(
    displayLarge = numbers.copy(fontSize = 52.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    titleLarge = numbers.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = numbers.copy(fontSize = 14.sp),
    bodySmall = numbers.copy(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
)

val Mono = FontFamily.Monospace

@Composable
fun TokenPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TP.accent,
            onPrimary = Color(0xFF1A0E08),
            secondary = TP.amber,
            background = TP.bg,
            onBackground = TP.text,
            surface = TP.surface,
            onSurface = TP.text,
            surfaceVariant = TP.surfaceHi,
            onSurfaceVariant = TP.muted,
            outline = TP.border,
            error = TP.danger,
        ),
        typography = type,
        content = content,
    )
}
