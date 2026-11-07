package com.bakery.labelapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF17130F)
val Paper = Color(0xFFF6F4F0)
val Line = Color(0xFFE4DED5)
val Outline = Color(0xFFDDD6CC)
val Muted = Color(0xFF6B6259)
val LabelInk = Color(0xFF4A433C)
val FieldBg = Color(0xFFFAF8F5)
val PreviewBg = Color(0xFFE9E4DC)
val Accent = Color(0xFF8F1D17)
val Danger = Color(0xFFB3261E)
val Good = Color(0xFF1E5A2E)
val GoodBg = Color(0xFFEEF7F0)
val WarnFg = Color(0xFF7A4A00)
val WarnBg = Color(0xFFFFF4E0)
val BadBg = Color(0xFFFCE8E6)

@Composable
fun BakeryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink,
            onPrimary = Color.White,
            secondary = Accent,
            onSecondary = Color.White,
            background = Paper,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Color(0xFFEDE8E0),
            onSurfaceVariant = Muted,
            outline = Outline,
            error = Danger,
        ),
        content = content,
    )
}
