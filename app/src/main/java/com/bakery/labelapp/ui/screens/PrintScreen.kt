package com.bakery.labelapp.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bakery.labelapp.label.Dates
import com.bakery.labelapp.ui.*

/** Standard sizes. 50 x 30 mm is the default (most common thermal label). */
val SIZE_PRESETS = listOf(
    Triple(40f, 25f, "Small"),
    Triple(50f, 30f, "Standard"),
    Triple(60f, 40f, "Medium"),
    Triple(75f, 50f, "Large"),
)

@Composable
fun PrintScreen(vm: AppViewModel, onAdmin: () -> Unit, onPrinter: () -> Unit, onBatches: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val printer by vm.printerStore.config.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity

    val product = vm.selectedProduct(products, form)
    val autoExp = vm.autoExp(product, form.mfgDay)
    val exp = form.manualExp ?: autoExp
    val copies = form.copies ?: settings.defaultCopies
    val content = vm.content(settings, product?.name ?: "PRODUCT NAME", form.mfgDay, exp)
    val datesOk = exp >= form.mfgDay

    var pickMfg by remember { mutableStateOf(false) }
    var pickExp by remember { mutableStateOf(false) }
    var thermalView by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Paper,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column(Modifier.background(Paper).statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(settings.bakeryName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Print Label", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    IconBox(Icons.Filled.History, "Batches", onBatches)
                    IconBox(Icons.Filled.Settings, "Admin", onAdmin)
                }
                PrinterChip(printer, onPrinter, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp))
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
                        onClick = { vm.exportPdf(activity) },
                        enabled = !busy && product != null && datesOk,
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("PDF")
                    }
                    Button(
                        onClick = { vm.printNew(activity) },
                        enabled = !busy && product != null && datesOk,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Print, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(if (copies == 1) "Print 1 label" else "Print $copies labels", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // ---- Live preview
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Live preview", Modifier.weight(1f))
                    Text("${sizeText(settings)} · $copies ${if (copies == 1) "copy" else "copies"}", fontSize = 12.sp, color = Muted)
                }
                LabelPreview(settings, content, mono = thermalView)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = thermalView, onCheckedChange = { thermalView = it })
                    Spacer(Modifier.width(10.dp))
                    Hint("Show how a 1-colour thermal printer prints it")
                }
            }

            // ---- Product
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Product")
                if (products.isEmpty()) {
                    Hint("No products yet. Add them in Admin → Products.")
                }
                products.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { p ->
                            ChoiceBox(
                                selected = p.id == product?.id,
                                title = p.name,
                                subtitle = "Shelf life ${Dates.days(p.shelfLifeDays)}",
                                modifier = Modifier.weight(1f),
                            ) { vm.selectProduct(p.id) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // ---- Dates
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Dates")
                CardBox {
                    DateField("Manufacturing date", Dates.format(form.mfgDay, settings.dateFormat)) { pickMfg = true }
                    if (form.mfgDay != Dates.today()) {
                        TextButton(onClick = { vm.setMfg(Dates.today()) }, contentPadding = PaddingValues(0.dp)) {
                            Text("Set to today", color = Accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Expiry date", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LabelInk, modifier = Modifier.weight(1f))
                        if (form.manualExp != null) Pill("Manual", BadBg, Accent) else Pill("Auto", GoodBg, Good)
                    }
                    DateField(null, Dates.format(exp, settings.dateFormat), color = Danger) { pickExp = true }
                    if (!datesOk) Hint("Expiry is before the manufacturing date.", Danger)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            Hint("Auto = MFG + ${Dates.days(product?.shelfLifeDays ?: 0)} (${Dates.format(autoExp, settings.dateFormat)})")
                        }
                        if (form.manualExp != null) {
                            TextButton(onClick = { vm.resetExp() }) { Text("Reset to auto", color = Accent, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }

            // ---- Label size
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Label size (mm)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SIZE_PRESETS.forEach { (w, h, tag) ->
                        ChoiceBox(
                            selected = settings.widthMm == w && settings.heightMm == h,
                            title = "${w.toInt()}×${h.toInt()}",
                            subtitle = if (tag == "Standard") "$tag ★" else tag,
                            modifier = Modifier.weight(1f),
                            center = true,
                        ) { vm.setSize(w, h) }
                    }
                }
                val custom = SIZE_PRESETS.none { it.first == settings.widthMm && it.second == settings.heightMm }
                Hint(if (custom) "Custom size in use: ${sizeText(settings)}. Change it in Admin → Label design."
                     else "Default is 50 × 30 mm. Any custom size can be set in Admin → Label design.")
            }

            // ---- Copies
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Number of prints")
                CardBox {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Stepper(copies, vm::setCopies)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 5, 10, 20).forEach { n ->
                            ChoiceBox(n == copies, "$n", null, Modifier.weight(1f), center = true) { vm.setCopies(n) }
                        }
                    }
                }
            }

            Hint("Each print is saved as a batch with fixed dates. Reprint any batch from the history button at the top.")
        }
    }

    if (pickMfg) DateDialog(form.mfgDay, onDismiss = { pickMfg = false }, onPick = vm::setMfg)
    if (pickExp) DateDialog(exp, onDismiss = { pickExp = false }, onPick = vm::setExp)
}
