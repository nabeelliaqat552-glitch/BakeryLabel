package com.bakery.labelapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakery.labelapp.data.LabelSettings
import com.bakery.labelapp.label.LabelContent
import com.bakery.labelapp.label.LabelRenderer
import com.bakery.labelapp.label.mm
import com.bakery.labelapp.print.PrinterConfig
import kotlin.math.min

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier,
        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = Muted,
    )
}

@Composable
fun Hint(text: String, color: Color = Muted) {
    Text(text, fontSize = 12.sp, color = color, lineHeight = 16.sp)
}

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun ChoiceBox(
    selected: Boolean,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    center: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(if (selected) Ink else Color.White)
            .border(1.dp, if (selected) Ink else Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            title, color = if (selected) Color.White else Ink,
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(subtitle, color = if (selected) Color(0xFFD9D3CB) else Muted, fontSize = 11.sp, maxLines = 2)
        }
    }
}

@Composable
fun IconBox(icon: ImageVector, description: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(44.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Outline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Ink)
    }
}

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.background(Paper).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) IconBox(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Column(Modifier.weight(1f)) {
                Text(eyebrow.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            }
            actions()
        }
        HorizontalDivider(color = Line)
    }
}

/** Number with − / + buttons and a typeable centre (1–999). */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int = 1, max: Int = 999) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalIconButton(
            onClick = { onChange((value - 1).coerceIn(min, max)) },
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Paper, contentColor = Ink),
        ) { Icon(Icons.Filled.Remove, contentDescription = "Fewer") }
        var text by remember(value) { mutableStateOf(value.toString()) }
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                text = t.filter(Char::isDigit).take(3)
                text.toIntOrNull()?.let { onChange(it.coerceIn(min, max)) }
            },
            singleLine = true,
            modifier = Modifier.width(92.dp),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        FilledIconButton(
            onClick = { onChange((value + 1).coerceIn(min, max)) },
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Ink, contentColor = Color.White),
        ) { Icon(Icons.Filled.Add, contentDescription = "More") }
    }
}

@Composable
fun DateField(label: String?, text: String, color: Color = Ink, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LabelInk)
        val shape = RoundedCornerShape(10.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(shape)
                .background(FieldBg)
                .border(1.dp, Outline, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick date", tint = Muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initialDay: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDay * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(it / 86_400_000L) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg,
    )
}

/** Live label preview, drawn by the same renderer used for printing. */
@Composable
fun LabelPreview(settings: LabelSettings, content: LabelContent, modifier: Modifier = Modifier, mono: Boolean = false) {
    BoxWithConstraints(
        modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(PreviewBg),
        contentAlignment = Alignment.Center,
    ) {
        val maxW = constraints.maxWidth * 0.9f
        val maxH = constraints.maxHeight * 0.85f
        val pxPerMm = min(maxW / settings.widthMm, maxH / settings.heightMm)
        val bmp = remember(settings, content, pxPerMm, mono) {
            LabelRenderer.bitmap(settings, content, pxPerMm, mono).asImageBitmap()
        }
        Image(bmp, contentDescription = "Label preview: ${content.productName}, expiry ${content.expText}")
    }
}

@Composable
fun PrinterChip(cfg: PrinterConfig, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ready = cfg.isConfigured
    val bg = if (ready) GoodBg else WarnBg
    val fg = if (ready) Good else WarnFg
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(fg))
        Text(
            if (ready) "${cfg.displayName} · ${cfg.type.label}" else "No printer · tap to set up",
            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

fun sizeText(s: LabelSettings) = "${mm(s.widthMm)} × ${mm(s.heightMm)} mm"
