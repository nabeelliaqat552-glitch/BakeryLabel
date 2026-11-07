package com.bakery.labelapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bakery.labelapp.data.LabelSettings
import com.bakery.labelapp.label.Dates
import com.bakery.labelapp.label.mm
import com.bakery.labelapp.ui.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun LabelDesignScreen(vm: AppViewModel, onBack: () -> Unit) {
    val saved by vm.settings.collectAsStateWithLifecycle()
    var d by remember(saved) { mutableStateOf(saved) }
    val today = Dates.today()
    val sample = vm.content(d, "CHOCOLATE CAKE", today, today + 2)

    Scaffold(
        containerColor = Paper,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column(Modifier.background(Paper)) {
                ScreenHeader("Admin", "Label design", onBack)
                Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { LabelPreview(d, sample) }
                HorizontalDivider(color = Line)
            }
        },
        bottomBar = {
            Column(Modifier.background(Color.White)) {
                HorizontalDivider(color = Line)
                Row(
                    Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { d = LabelSettings(adminPin = saved.adminPin, updatedAt = saved.updatedAt) },
                        modifier = Modifier.height(52.dp), shape = RoundedCornerShape(14.dp),
                    ) { Text("Reset") }
                    Button(
                        onClick = { vm.saveSettings(d) }, enabled = d != saved,
                        modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp),
                    ) { Text("Save design", fontWeight = FontWeight.Bold) }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Branding")
            CardBox {
                OutlinedTextField(
                    value = d.bakeryName, onValueChange = { d = d.copy(bakeryName = it.uppercase().take(30)) },
                    label = { Text("Bakery name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                OutlinedTextField(
                    value = d.subtitle, onValueChange = { d = d.copy(subtitle = it.uppercase().take(40)) },
                    label = { Text("Subtitle") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show subtitle", modifier = Modifier.weight(1f))
                    Switch(checked = d.showSubtitle, onCheckedChange = { d = d.copy(showSubtitle = it) })
                }
            }

            SectionTitle("Label size (mm)")
            CardBox {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("Width", d.widthMm, { d = d.copy(widthMm = it) }, Modifier.weight(1f), 15f, 120f)
                    NumberField("Height", d.heightMm, { d = d.copy(heightMm = it) }, Modifier.weight(1f), 10f, 120f)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SIZE_PRESETS.forEach { (w, h, _) ->
                        ChoiceBox(d.widthMm == w && d.heightMm == h, "${w.toInt()}×${h.toInt()}", null, Modifier.weight(1f), center = true) {
                            d = d.copy(widthMm = w, heightMm = h)
                        }
                    }
                }
                NumberField("Gap between labels (mm)", d.gapMm, { d = d.copy(gapMm = it) }, Modifier.fillMaxWidth(), 0f, 20f)
                Hint("Default 50 × 30 mm with a 2 mm gap — the most common thermal label roll.")
            }

            SectionTitle("Text & border")
            CardBox {
                SliderRow("Font size", d.fontScale, 0.7f..1.4f, "${(d.fontScale * 100).roundToInt()}%") { d = d.copy(fontScale = it) }
                SliderRow("Border thickness", d.borderMm, 0f..1f, "${fmt1(d.borderMm)} mm") { d = d.copy(borderMm = it) }
                SliderRow("Corner roundness", d.cornerMm, 0f..5f, "${fmt1(d.cornerMm)} mm") { d = d.copy(cornerMm = it) }
            }

            SectionTitle("Colours")
            CardBox {
                ColorRow("Background", d.backgroundColor, listOf(0xFF000000, 0xFF1A1A1A, 0xFF2B1D14, 0xFFFFFFFF)) { d = d.copy(backgroundColor = it) }
                HorizontalDivider(color = Line)
                ColorRow("Text", d.textColor, listOf(0xFFFFFFFF, 0xFFF2E6D0, 0xFF000000, 0xFFD4AF37)) { d = d.copy(textColor = it) }
                HorizontalDivider(color = Line)
                ColorRow("Border", d.borderColor, listOf(0xFFCFCFCF, 0xFFFFFFFF, 0xFF6E6E6E, 0xFFD4AF37)) { d = d.copy(borderColor = it) }
                HorizontalDivider(color = Line)
                ColorRow("Expiry date", d.expiryColor, listOf(0xFFFF3B30, 0xFFE53935, 0xFFFFD60A, 0xFFFFFFFF)) { d = d.copy(expiryColor = it) }
                Hint("Thermal label printers print in one colour only, so the red expiry date prints white on black there. Colours show fully in PDF and colour printers.")
            }

            SectionTitle("Date format")
            Dates.FORMATS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { f ->
                        ChoiceBox(d.dateFormat == f, Dates.format(today, f), null, Modifier.weight(1f), center = true) {
                            d = d.copy(dateFormat = f)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            SectionTitle("Default number of prints")
            CardBox {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Stepper(d.defaultCopies, { d = d.copy(defaultCopies = it) })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun fmt1(v: Float) = String.format(Locale.US, "%.1f", v)

@Composable
private fun NumberField(label: String, value: Float, onChange: (Float) -> Unit, modifier: Modifier, min: Float, max: Float) {
    var text by remember(value) { mutableStateOf(mm(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter { it.isDigit() || it == '.' }.take(5)
            text.toFloatOrNull()?.let { if (it in min..max) onChange(it) }
        },
        label = { Text(label) },
        singleLine = true,
        isError = text.toFloatOrNull()?.let { it !in min..max } ?: true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = 12.sp, color = Muted)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Ink, activeTrackColor = Ink),
        )
    }
}

private fun hex(v: Long) = "%06X".format(v and 0xFFFFFF)

@Composable
private fun ColorRow(label: String, value: Long, swatches: List<Long>, onChange: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            swatches.forEach { c ->
                val selected = (c and 0xFFFFFFFF) == (value and 0xFFFFFFFF)
                val shape = RoundedCornerShape(10.dp)
                Box(
                    Modifier.size(36.dp).clip(shape).background(Color(c))
                        .border(if (selected) 3.dp else 1.dp, if (selected) Accent else Outline, shape)
                        .clickable { onChange(c) }
                )
            }
            var text by remember(value) { mutableStateOf(hex(value)) }
            OutlinedTextField(
                value = text,
                onValueChange = { t ->
                    text = t.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                    if (text.length == 6) onChange(0xFF000000L or text.toLong(16))
                },
                prefix = { Text("#") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
