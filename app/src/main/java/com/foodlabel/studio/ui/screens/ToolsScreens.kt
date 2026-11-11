package com.foodlabel.studio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodlabel.studio.backup.BackupContents
import com.foodlabel.studio.core.Units
import com.foodlabel.studio.data.db.LabelSizeEntity
import com.foodlabel.studio.printing.JobStatus
import com.foodlabel.studio.ui.BatchCart
import com.foodlabel.studio.ui.LocalBusiness
import com.foodlabel.studio.ui.LocalContainer
import com.foodlabel.studio.ui.Routes
import com.foodlabel.studio.ui.components.CardBlock
import com.foodlabel.studio.ui.components.ConfirmDialog
import com.foodlabel.studio.ui.components.EmptyState
import com.foodlabel.studio.ui.components.Field
import com.foodlabel.studio.ui.components.Notice
import com.foodlabel.studio.ui.components.NoticeKind
import com.foodlabel.studio.ui.components.NumberField
import com.foodlabel.studio.ui.components.PrimaryButton
import com.foodlabel.studio.ui.components.ScreenHeader
import com.foodlabel.studio.ui.components.SecondaryButton
import com.foodlabel.studio.ui.components.SectionLabel
import com.foodlabel.studio.ui.components.Segmented
import com.foodlabel.studio.ui.components.ToggleRow
import com.foodlabel.studio.ui.theme.Fls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String, first: Boolean = false, onClick: () -> Unit) {
    if (!first) HorizontalDivider(color = Fls.LineSoft)
    Surface(onClick = onClick, color = Fls.Card) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Fls.Sand), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Fls.GoldDeep) }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(sub, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Fls.Muted)
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val c = LocalContainer.current
    val business = LocalBusiness.current
    val printers by remember(business.id) { c.repo.printers(business.id) }.collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0" }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings & Tools", business.name, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel("Printing")
            CardBlock(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                SettingRow(Icons.Outlined.Bluetooth, "Printer Manager", printers.firstOrNull()?.let { "${it.name} · add or test printers" } ?: "No printer yet · add one", first = true) { onNavigate(Routes.PRINTERS) }
                SettingRow(Icons.Outlined.Straighten, "Label Sizes", "Ready-made and custom sizes, mm or inches") { onNavigate(Routes.SIZES) }
                SettingRow(Icons.Outlined.Layers, "Batch Printing", "Several products in one go · ${BatchCart.items.size} waiting") { onNavigate(Routes.BATCH) }
                SettingRow(Icons.Outlined.History, "Print History", "What was sent, and print again") { onNavigate(Routes.HISTORY) }
            }
            SectionLabel("Business & data")
            CardBlock(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                SettingRow(Icons.Outlined.Storefront, "My Businesses", "Switch, add, duplicate, archive", first = true) { onNavigate(Routes.BUSINESSES) }
                SettingRow(Icons.Outlined.Tune, "This business", "Name, logo, currency (${business.currencyCode}), date format, label language") { onNavigate(Routes.business(business.id)) }
                SettingRow(Icons.Outlined.Save, "Backup & Restore", "ZIP backup, CSV and JSON") { onNavigate(Routes.BACKUP) }
            }
            SectionLabel("About")
            CardBlock {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = Fls.GoldDeep)
                    Column {
                        Text("FoodLabel Studio $version", fontWeight = FontWeight.Bold)
                        Text("Works fully offline. No account. Your business data stays on this phone unless you export a backup yourself. " +
                            "The only network use is sending labels to a Wi-Fi/LAN printer you set up.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun LabelSizesScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val business = LocalBusiness.current
    val scope = rememberCoroutineScope()
    val sizes by remember(business.id) { c.repo.labelSizes(business.id) }.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<LabelSizeEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Label Sizes", "Tap a size to make it the default for ${business.name}", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val e = editing
            if (e != null) SizeEditor(e, onDone = { editing = null })
            sizes.forEach { s ->
                val isDefault = business.defaultLabelSizeId == s.id
                Surface(onClick = { scope.launch { c.repo.updateBusiness(business.copy(defaultLabelSizeId = s.id)) } }, shape = RoundedCornerShape(16.dp),
                    color = if (isDefault) Fls.Chocolate else Fls.Card, border = androidx.compose.foundation.BorderStroke(1.dp, if (isDefault) Fls.Chocolate else Fls.Line),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontWeight = FontWeight.Bold, color = if (isDefault) Fls.Ivory else Fls.Ink)
                            Text(
                                "${Units.formatMm(s.widthMm)} × ${Units.formatMm(s.heightMm)} mm · ${"%.2f".format(Locale.ENGLISH, Units.mmToInch(s.widthMm))} × ${"%.2f".format(Locale.ENGLISH, Units.mmToInch(s.heightMm))} in" +
                                    (if (s.continuous) " · continuous" else " · gap ${Units.formatMm(s.gapMm)} mm") + (if (s.businessId == null) "" else " · custom") + if (isDefault) " · default" else "",
                                style = MaterialTheme.typography.bodySmall, color = if (isDefault) Fls.Sand else Fls.Muted,
                            )
                        }
                        if (s.businessId != null) {
                            TextButton({ editing = s }) { Text("Edit", color = if (isDefault) Fls.GoldPale else Fls.Chocolate) }
                            IconButton({ scope.launch { c.repo.deleteLabelSize(s) } }) { Icon(Icons.Outlined.Delete, "Delete size", tint = if (isDefault) Fls.GoldPale else Fls.Danger) }
                        }
                    }
                }
            }
        }
        Box(Modifier.padding(16.dp).navigationBarsPadding()) {
            PrimaryButton("Add custom size", { editing = LabelSizeEntity(businessId = business.id, name = "", widthMm = 50.0, heightMm = 30.0) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SizeEditor(initial: LabelSizeEntity, onDone: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var s by remember(initial.id) { mutableStateOf(initial) }
    var inch by remember(initial.id) { mutableStateOf(initial.displayUnit == "INCH") }
    var swaps by remember(initial.id) { mutableStateOf(0) }
    fun shown(mm: Double) = if (inch) Units.mmToInch(mm) else mm
    fun toMm(v: Double) = if (inch) Units.inchToMm(v) else v
    val unit = if (inch) "in" else "mm"

    CardBlock {
        SectionLabel(if (initial.id == 0L) "New custom size" else "Edit size")
        Field(s.name, { s = s.copy(name = it) }, "Name (optional)")
        Segmented(listOf(false, true), inch, { if (it) "Inches" else "Millimetres" }, { inch = it }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(shown(s.widthMm), { s = s.copy(widthMm = toMm(it)) }, "Width ($unit)", Modifier.weight(1f), resetKey = "$inch/$swaps")
            NumberField(shown(s.heightMm), { s = s.copy(heightMm = toMm(it)) }, "Height ($unit)", Modifier.weight(1f), resetKey = "$inch/$swaps")
        }
        SecondaryButton("Swap to ${if (s.widthMm >= s.heightMm) "portrait" else "landscape"}", {
            s = s.copy(widthMm = s.heightMm, heightMm = s.widthMm, orientation = if (s.heightMm >= s.widthMm) "LANDSCAPE" else "PORTRAIT"); swaps++
        }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(shown(s.gapMm), { s = s.copy(gapMm = toMm(it)) }, "Label gap ($unit)", Modifier.weight(1f), resetKey = "$inch/$swaps")
            NumberField(shown(s.paperWidthMm ?: s.widthMm), { s = s.copy(paperWidthMm = toMm(it)) }, "Paper width ($unit)", Modifier.weight(1f), resetKey = "$inch/$swaps")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(shown(s.marginHMm), { s = s.copy(marginHMm = toMm(it)) }, "Side margin ($unit)", Modifier.weight(1f), resetKey = "$inch/$swaps")
            NumberField(shown(s.marginVMm), { s = s.copy(marginVMm = toMm(it)) }, "Top margin ($unit)", Modifier.weight(1f), resetKey = "$inch/$swaps")
        }
        ToggleRow("Continuous paper (no gaps)", s.continuous, { s = s.copy(continuous = it) })
        ToggleRow("Round label", s.shape == "ROUND", { s = s.copy(shape = if (it) "ROUND" else "RECT") })
        val valid = s.widthMm in 8.0..300.0 && s.heightMm in 5.0..500.0
        if (!valid) Notice("Width must be 8–300 mm and height 5–500 mm.", NoticeKind.Warning)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Cancel", onDone, modifier = Modifier.weight(1f))
            PrimaryButton("Save size", {
                scope.launch {
                    val name = s.name.ifBlank { "${Units.formatMm(s.widthMm)} × ${Units.formatMm(s.heightMm)} mm" }
                    c.repo.saveLabelSize(s.copy(name = name, displayUnit = if (inch) "INCH" else "MM")); onDone()
                }
            }, modifier = Modifier.weight(1f), enabled = valid, height = 52.dp)
        }
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val c = LocalContainer.current
    val business = LocalBusiness.current
    val scope = rememberCoroutineScope()
    val jobs by remember(business.id) { c.repo.jobs(business.id) }.collectAsStateWithLifecycle(emptyList())
    val df = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH) }
    val printed = jobs.filter { it.status != JobStatus.SAVED.name }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Print History", "${business.name} · last ${printed.size} jobs", onBack)
        if (printed.isEmpty()) EmptyState("Nothing printed yet", "Every print job appears here with what was sent and whether you confirmed it.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            printed.forEach { j ->
                val status = JobStatus.parse(j.status)
                var lines by remember(j.id) { mutableStateOf<String?>(null) }
                androidx.compose.runtime.LaunchedEffect(j.id) {
                    lines = c.repo.jobHistory(j.id).joinToString("\n") { "${it.copies} × ${it.productName} · EXP ${it.expDate.ifBlank { "—" }}" + if (it.batchNumber.isNotBlank()) " · ${it.batchNumber}" else "" }
                }
                CardBlock {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(df.format(Date(j.createdAt)), fontWeight = FontWeight.Bold)
                            Text("${j.printerName} · ${j.sentLabels} of ${j.totalLabels} labels sent", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(status.label, fontWeight = FontWeight.Bold, color = when (status) {
                            JobStatus.CONFIRMED -> Fls.Success; JobStatus.FAILED -> Fls.Danger; JobStatus.SENT -> Fls.Warning; else -> Fls.Muted
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                    lines?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    j.error?.let { Notice(it, NoticeKind.Error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (status == JobStatus.SENT) {
                            SecondaryButton("Had a problem", { scope.launch { c.printQueue.confirm(j.id, false) } }, modifier = Modifier.weight(1f))
                            SecondaryButton("Printed OK", { scope.launch { c.printQueue.confirm(j.id, true) } }, modifier = Modifier.weight(1f))
                        }
                        SecondaryButton("Print again", {
                            BatchCart.items.clear()
                            // Same products and quantities, with today's manufacturing date; manual expiry dates are kept.
                            BatchCart.items.addAll(c.printQueue.decodeItems(j.payload).map { it.copy(mfgDate = java.time.LocalDate.now().toString()) })
                            onNavigate(Routes.BATCH)
                        }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val business = LocalBusiness.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<Pair<String, NoticeKind>?>(null) }
    var pending by remember { mutableStateOf<BackupContents?>(null) }
    var askReplace by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("business") }

    fun task(okText: String, block: suspend () -> Unit) = scope.launch {
        message = runCatching { withContext(Dispatchers.IO) { block() }; okText to NoticeKind.Success }
            .getOrElse { (it.message ?: "Something went wrong.") to NoticeKind.Error }
    }

    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) task("Backup saved.") {
            context.contentResolver.openOutputStream(uri)?.use { if (mode == "all") c.backup.exportAllZip(it) else c.backup.exportZip(listOf(business.id), it) }
        }
    }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) task("JSON saved.") { context.contentResolver.openOutputStream(uri)?.use { c.backup.exportJson(business.id, it) } }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) task("Products exported.") {
            val csv = c.backup.productsCsv(business.id)
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { c.backup.read(it) } } }
                .onSuccess { pending = it }
                .onFailure { message = ("This file could not be read as a FoodLabel Studio backup. " + (it.message ?: "")) to NoticeKind.Error }
        }
    }
    val stamp = remember { SimpleDateFormat("yyyyMMdd-HHmm", Locale.ENGLISH).format(Date()) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Backup & Restore", "Files are saved where you choose. Nothing is uploaded.", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            message?.let { Notice(it.first, it.second) }
            CardBlock {
                SectionLabel("Back up")
                Text("A backup ZIP holds products, categories, label designs and templates, sizes, printers and print history. " +
                    "Copy it to another phone and restore it there to move your data.", style = MaterialTheme.typography.bodyMedium)
                PrimaryButton("Back up ${business.name}", { mode = "business"; saveZip.launch("FoodLabel-${business.name}-$stamp.zip") }, Modifier.fillMaxWidth())
                SecondaryButton("Back up all businesses", { mode = "all"; saveZip.launch("FoodLabel-all-$stamp.zip") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Products CSV", { saveCsv.launch("${business.name}-products.csv") }, modifier = Modifier.weight(1f))
                    SecondaryButton("JSON", { saveJson.launch("${business.name}.json") }, modifier = Modifier.weight(1f))
                }
            }
            CardBlock {
                SectionLabel("Restore")
                Text("Choose a backup ZIP (or JSON). You will see what is inside before anything changes.", style = MaterialTheme.typography.bodyMedium)
                SecondaryButton("Choose backup file", { openBackup.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth())
                pending?.let { p ->
                    Notice("This backup has ${p.bundles.size} business(es): ${p.bundles.joinToString { it.business.name }} with ${p.products} products in total.", NoticeKind.Info)
                    PrimaryButton("Add as new business", { val pp = p; pending = null; task("Restored as new business. Existing data was not changed.") { c.backup.restore(pp, false) } }, Modifier.fillMaxWidth())
                    SecondaryButton("Replace businesses with the same name", { askReplace = true }, modifier = Modifier.fillMaxWidth(), danger = true)
                }
            }
        }
    }
    val p = pending
    if (askReplace && p != null) ConfirmDialog(
        "Overwrite existing data?",
        "Businesses on this phone named ${p.bundles.joinToString { it.business.name }} will be deleted and replaced by the backup. This cannot be undone.",
        "Overwrite", { askReplace = false; pending = null; task("Restored. Matching businesses were replaced.") { c.backup.restore(p, true) } },
        { askReplace = false }, danger = true,
    )
}
