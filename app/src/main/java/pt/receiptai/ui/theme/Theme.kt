package pt.receiptai.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Cores tiradas do logótipo ReceiptAI.
val Navy = Color(0xFF12304A)
val NavyDark = Color(0xFF0F2539)
val NavyCard = Color(0xFF142C43)
val Green = Color(0xFF1E9E5A)
val GreenLight = Color(0xFF2FBF73)
val Mint = Color(0xFFE8F7EF)
val MintDeep = Color(0xFFD4EEE0)
val Slate = Color(0xFF546777)
val SlateLight = Color(0xFF7D9093)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = MintDeep,
    onPrimaryContainer = Navy,
    secondary = Slate,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE6EC),
    onSecondaryContainer = Navy,
    tertiary = Navy,
    onTertiary = Color.White,
    background = Mint,
    onBackground = Navy,
    surface = Mint,
    onSurface = Navy,
    surfaceVariant = Color(0xFFDDE9E3),
    onSurfaceVariant = Slate,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF4FBF7),
    surfaceContainerHigh = Color(0xFFEFF7F3),
    surfaceContainerHighest = Color(0xFFE2EEE8),
    outline = Color(0xFFA8C4BD),
    outlineVariant = Color(0xFFCFE0D8),
    error = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = NavyDark,
    primaryContainer = Color(0xFF1B4A3A),
    onPrimaryContainer = Mint,
    secondary = SlateLight,
    onSecondary = NavyDark,
    secondaryContainer = Color(0xFF243F57),
    onSecondaryContainer = Mint,
    tertiary = Mint,
    onTertiary = NavyDark,
    background = NavyDark,
    onBackground = Mint,
    surface = NavyDark,
    onSurface = Mint,
    surfaceVariant = Color(0xFF1C3650),
    onSurfaceVariant = Color(0xFFA7B8C2),
    surfaceContainerLowest = Color(0xFF0B1D2D),
    surfaceContainerLow = NavyCard,
    surfaceContainer = NavyCard,
    surfaceContainerHigh = Color(0xFF1A3550),
    surfaceContainerHighest = Color(0xFF203D5A),
    outline = Color(0xFF4B6478),
    outlineVariant = Color(0xFF2A4560),
    error = Color(0xFFEF5350),
)

/** Cores extra para os estados de validade (não existem no Material). */
@Immutable
data class StatusColors(val expired: Color, val soon: Color, val ok: Color)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(expired = Color(0xFFC62828), soon = Color(0xFFE0A100), ok = Green)
}

/** Curva "ease-out" forte (skill animate): entradas e aberturas de menus. */
val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-1).sp),
    )
}

@Composable
fun ReceiptAiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val status = if (darkTheme) StatusColors(Color(0xFFEF6C6C), Color(0xFFFFC94D), GreenLight)
    else StatusColors(Color(0xFFC62828), Color(0xFFE0A100), Green)
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides status) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = AppTypography, content = content)
    }
}
