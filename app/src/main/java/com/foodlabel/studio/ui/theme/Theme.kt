package com.foodlabel.studio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette from the approved design: ivory and beige, chocolate brown, a little gold. */
object Fls {
    val Ivory = Color(0xFFFBF7EF)
    val Card = Color(0xFFFFFDF8)
    val Sand = Color(0xFFEFE5D3)
    val SandDeep = Color(0xFFF3EADB)
    val Line = Color(0xFFE2D5BD)
    val LineSoft = Color(0xFFEFE5D3)
    val Chocolate = Color(0xFF3A2315)
    val Ink = Color(0xFF2E1C12)
    val Muted = Color(0xFF6E5443)
    val Gold = Color(0xFFD9B665)
    val GoldPale = Color(0xFFF6E7BF)
    val GoldDeep = Color(0xFF86651C)
    val GoldRing = Color(0xFFB8923F)
    val Success = Color(0xFF2F5A27)
    val SuccessBg = Color(0xFFE8F1E3)
    val Warning = Color(0xFF5A4210)
    val WarningBg = Color(0xFFFFF6E0)
    val Danger = Color(0xFF9A3B22)
    val DangerBg = Color(0xFFFBF1EC)

    /** Display face. A bundled serif keeps the app fully offline. */
    val Display = FontFamily.Serif
}

private val colors = lightColorScheme(
    primary = Fls.Chocolate,
    onPrimary = Fls.Ivory,
    primaryContainer = Fls.Sand,
    onPrimaryContainer = Fls.Ink,
    secondary = Fls.GoldDeep,
    onSecondary = Color.White,
    secondaryContainer = Fls.GoldPale,
    onSecondaryContainer = Fls.Ink,
    tertiary = Fls.Gold,
    background = Fls.Ivory,
    onBackground = Fls.Ink,
    surface = Fls.Card,
    onSurface = Fls.Ink,
    surfaceVariant = Fls.Sand,
    onSurfaceVariant = Fls.Muted,
    surfaceContainer = Fls.Card,
    surfaceContainerHigh = Fls.Card,
    surfaceContainerHighest = Fls.Sand,
    surfaceContainerLow = Fls.Ivory,
    outline = Fls.Line,
    outlineVariant = Fls.LineSoft,
    error = Fls.Danger,
    errorContainer = Fls.DangerBg,
)

private val type = Typography(
    headlineLarge = TextStyle(fontFamily = Fls.Display, fontSize = 30.sp, lineHeight = 36.sp, color = Fls.Ink),
    headlineMedium = TextStyle(fontFamily = Fls.Display, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Fls.Display, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Fls.Display, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, color = Fls.Muted),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FoodLabelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, shapes = shapes, content = content)
}
