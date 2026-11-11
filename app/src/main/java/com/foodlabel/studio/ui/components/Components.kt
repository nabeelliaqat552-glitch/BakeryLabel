package com.foodlabel.studio.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.BreakfastDining
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.EmojiFoodBeverage
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Icecream
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.KebabDining
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.SetMeal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodlabel.studio.label.LabelData
import com.foodlabel.studio.label.LabelDesign
import com.foodlabel.studio.label.LabelRenderer
import com.foodlabel.studio.label.RenderResult
import com.foodlabel.studio.ui.theme.Fls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) RoundIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = Fls.Ink, enabled: Boolean = true) {
    Surface(
        onClick = onClick, enabled = enabled, shape = CircleShape, color = Fls.Card,
        border = BorderStroke(1.dp, Fls.Line), modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, tint = if (enabled) tint else Fls.Line) }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Fls.GoldDeep, modifier = modifier)
}

@Composable
fun CardBlock(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Fls.Card)
            .border(1.dp, Fls.Line, RoundedCornerShape(18.dp)).padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, height: Dp = 56.dp) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = height),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Fls.Chocolate, contentColor = Fls.Ivory, disabledContainerColor = Fls.Line),
    ) {
        if (icon != null) { Icon(icon, null, tint = Fls.Gold); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, fontSize = 16.sp)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, danger: Boolean = false) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, if (danger) Fls.Danger else Fls.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) Fls.Danger else Fls.Ink),
    ) {
        if (icon != null) { Icon(icon, null); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(20.dp),
        color = if (selected) Fls.Chocolate else Fls.Card,
        border = BorderStroke(1.dp, if (selected) Fls.Chocolate else Fls.Line),
        modifier = modifier.heightIn(min = 40.dp),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, color = if (selected) Fls.Ivory else Fls.Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Segmented control, e.g. Days / Weeks / Months. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(Fls.Sand).padding(3.dp)) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (on) Fls.Card else Color.Transparent).clickable { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) { Text(label(o), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (on) Fls.Ink else Fls.Muted, maxLines = 1) }
        }
    }
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int = 1, max: Int = 9999, label: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(onClick = { if (value > min) onChange(value - 1) }, shape = CircleShape, color = Fls.Ivory,
            border = BorderStroke(1.dp, Fls.Line), modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("−", fontSize = 22.sp, color = Fls.Ink) }
        }
        Text(value.toString() + (label?.let { " $it" } ?: ""), fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 36.dp), textAlign = TextAlign.Center)
        Surface(onClick = { if (value < max) onChange(value + 1) }, shape = CircleShape, color = Fls.Chocolate, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("+", fontSize = 22.sp, color = Fls.GoldPale) }
        }
    }
}

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text, rtl: Boolean = false, singleLine: Boolean = true, supporting: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(),
        singleLine = singleLine, isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            textDirection = if (rtl) TextDirection.Rtl else TextDirection.Content,
            fontSize = if (rtl) 18.sp else 16.sp,
        ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Fls.Chocolate, unfocusedBorderColor = Fls.Line,
            focusedContainerColor = Fls.Ivory, unfocusedContainerColor = Fls.Ivory,
        ),
    )
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(
            checkedTrackColor = Fls.Chocolate, checkedThumbColor = Fls.Gold, uncheckedTrackColor = Fls.Line, uncheckedThumbColor = Fls.Card,
            uncheckedBorderColor = Fls.Line,
        ))
    }
}

@Composable
fun InfoRow(label: String, value: String, strong: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Fls.Muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Notice(text: String, kind: NoticeKind = NoticeKind.Info, modifier: Modifier = Modifier) {
    val (bg, fg) = when (kind) {
        NoticeKind.Info -> Fls.Sand to Fls.Ink
        NoticeKind.Success -> Fls.SuccessBg to Fls.Success
        NoticeKind.Warning -> Fls.WarningBg to Fls.Warning
        NoticeKind.Error -> Fls.DangerBg to Fls.Danger
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (kind == NoticeKind.Warning || kind == NoticeKind.Error) Icon(Icons.Outlined.Warning, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, color = fg, style = MaterialTheme.typography.bodyMedium)
    }
}

enum class NoticeKind { Info, Success, Warning, Error }

@Composable
fun ConfirmDialog(
    title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = if (danger) Fls.Danger else Fls.Chocolate, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Fls.Muted) } },
        containerColor = Fls.Card,
    )
}

@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Fls.Muted, textAlign = TextAlign.Center)
        if (action != null) PrimaryButton(action, onAction)
    }
}

/**
 * Shows a label exactly as it will print: rendered off the main thread at [dpi] (the printer's DPI)
 * with a 2× supersample, then shown at the label's aspect ratio.
 */
@Composable
fun LabelPreview(
    design: LabelDesign?, data: LabelData, dpi: Int, modifier: Modifier = Modifier,
    onRendered: (RenderResult) -> Unit = {},
) {
    val result by produceState<RenderResult?>(null, design, data, dpi) {
        value = if (design == null) null else withContext(Dispatchers.Default) {
            runCatching { LabelRenderer.render(design, data, dpi, scale = 2) }.getOrNull()
        }
        value?.let(onRendered)
    }
    val ratio = design?.let { (it.widthMm / it.heightMm).toFloat() } ?: 1.6f
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Fls.Sand).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val bmp: Bitmap? = result?.bitmap
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.3f, 5f)).clip(RoundedCornerShape(if (design?.round == true) 999.dp else 6.dp))
                .background(Color.White),
        ) {
            if (bmp != null) Image(bmp.asImageBitmap(), "Label preview", contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium, modifier = Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.3f, 5f)))
        }
    }
}

fun categoryIcon(key: String): ImageVector = when (key) {
    "cake" -> Icons.Outlined.Cake
    "cookie" -> Icons.Outlined.Cookie
    "bread" -> Icons.Outlined.BakeryDining
    "croissant" -> Icons.Outlined.BakeryDining
    "rusk" -> Icons.Outlined.BreakfastDining
    "sweets" -> Icons.Outlined.Icecream
    "milk" -> Icons.Outlined.LocalDrink
    "burger" -> Icons.Outlined.LunchDining
    "frozen" -> Icons.Outlined.AcUnit
    "snack" -> Icons.Outlined.Fastfood
    "drink" -> Icons.Outlined.EmojiFoodBeverage
    "candy" -> Icons.Outlined.Cookie
    "meat" -> Icons.Outlined.KebabDining
    "fish" -> Icons.Outlined.SetMeal
    else -> Icons.Outlined.Inventory2
}

val CATEGORY_ICON_KEYS = listOf("cake", "cookie", "bread", "croissant", "rusk", "sweets", "milk", "burger", "frozen", "snack", "drink", "candy", "meat", "fish", "box")
val CATEGORY_TINTS = listOf(0xFFF3DDD3, 0xFFF0E3C8, 0xFFEEDFC6, 0xFFF2E6CC, 0xFFE9D8BD, 0xFFF1D9DC, 0xFFDFE9EC, 0xFFF0D9C6, 0xFFDCE6EE, 0xFFE1E8DA, 0xFFEEDCE6, 0xFFEDD8D2)

@Composable
fun UrduText(text: String, modifier: Modifier = Modifier, size: Int = 15, color: Color = Fls.Ink) {
    Text(text, modifier = modifier, fontSize = size.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl, lineHeight = (size * 1.8).sp))
}

@Composable
fun BackButtonOnly(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
}

@Composable
fun Gap(h: Int = 8) = Spacer(Modifier.height(h.dp))

/** Decimal input that lets the user type freely ("0.", "12,5") and reports a value whenever it parses. */
@Composable
fun NumberField(value: Double, onValue: (Double) -> Unit, label: String, modifier: Modifier = Modifier, resetKey: Any? = null) {
    var text by androidx.compose.runtime.remember(resetKey) {
        androidx.compose.runtime.mutableStateOf(com.foodlabel.studio.core.Units.formatMm(value))
    }
    Field(text, { t ->
        text = t
        t.replace(',', '.').toDoubleOrNull()?.let(onValue)
    }, label, modifier, keyboard = KeyboardType.Decimal)
}
