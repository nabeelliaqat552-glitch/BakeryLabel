package com.foodlabel.studio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodlabel.studio.data.db.PrinterEntity
import com.foodlabel.studio.data.repo.ProductWithBarcode
import com.foodlabel.studio.data.seed.TemplateLibrary
import com.foodlabel.studio.dates.DateEngine
import com.foodlabel.studio.dates.DateFormatOption
import com.foodlabel.studio.dates.ShelfLifeUnit
import com.foodlabel.studio.label.LabelDataFactory
import com.foodlabel.studio.label.LabelDesign
import com.foodlabel.studio.label.Tokens
import com.foodlabel.studio.printing.BatchItem
import com.foodlabel.studio.printing.Output
import com.foodlabel.studio.ui.BatchCart
import com.foodlabel.studio.ui.LocalBusiness
import com.foodlabel.studio.ui.LocalContainer
import com.foodlabel.studio.ui.Routes
import com.foodlabel.studio.ui.components.CardBlock
import com.foodlabel.studio.ui.components.DatePickDialog
import com.foodlabel.studio.ui.components.Field
import com.foodlabel.studio.ui.components.InfoRow
import com.foodlabel.studio.ui.components.LabelPreview
import com.foodlabel.studio.ui.components.Notice
import com.foodlabel.studio.ui.components.NoticeKind
import com.foodlabel.studio.ui.components.PrimaryButton
import com.foodlabel.studio.ui.components.ScreenHeader
import com.foodlabel.studio.ui.components.SecondaryButton
import com.foodlabel.studio.ui.components.SectionLabel
import com.foodlabel.studio.ui.components.Segmented
import com.foodlabel.studio.ui.components.Stepper
import com.foodlabel.studio.ui.components.UrduText
import com.foodlabel.studio.ui.theme.Fls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun PrinterPicker(printers: List<PrinterEntity>, selectedId: Long?, onSelect: (Long) -> Unit, onSetup: () -> Unit) {
    if (printers.isEmpty()) {
        Notice("No printer is set up for this business yet. You can still save labels as PDF.", NoticeKind.Warning)
        SecondaryButton("Set up a printer", onSetup, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.Print)
        return
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        printers.forEach { p ->
            com.foodlabel.studio.ui.components.Chip("${p.name}${if (p.verified) " ✓" else ""}", p.id == selectedId, { onSelect(p.id) })
        }
    }
}

/** Builds the design a product prints with: its own saved design, or a starter layout at the business's default size. */
suspend fun designForProduct(c: com.foodlabel.studio.AppContainer, defaultDesignId: Long?, sizeId: Long?): LabelDesign {
    defaultDesignId?.let { c.repo.getDesign(it)?.let { d -> return d } }
    val size = sizeId?.let { c.repo.getLabelSize(it) }
    return TemplateLibrary.blankStarter(size?.widthMm ?: 50.0, size?.heightMm ?: 30.0)
}

@Composable
fun QuickPrintScreen(initialProductId: Long?, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val c = LocalContainer.current
    val business = LocalBusiness.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fmt = DateFormatOption.parse(business.dateFormat)

    var query by rememberSaveable { mutableStateOf("") }
    val products by remember(business.id, query) { c.repo.searchProducts(business.id, query, null, false) }
        .collectAsStateWithLifecycle(emptyList())
    val printers by remember(business.id) { c.repo.printers(business.id) }.collectAsStateWithLifecycle(emptyList())

    var selectedId by rememberSaveable { mutableStateOf(initialProductId ?: -1L) }
    var selected by remember { mutableStateOf<ProductWithBarcode?>(null) }
    var design by remember { mutableStateOf<LabelDesign?>(null) }
    var mfg by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var manualExpiry by rememberSaveable { mutableStateOf<String?>(null) }
    var batch by rememberSaveable { mutableStateOf("") }
    var copies by rememberSaveable { mutableStateOf(1) }
    var language by rememberSaveable { mutableStateOf(business.labelLanguage) }
    var printerId by rememberSaveable { mutableStateOf(-1L) }
    var pickMfg by remember { mutableStateOf(false) }
    var pickExp by remember { mutableStateOf(false) }
    var jobId by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var warnings by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(products, selectedId) {
        if (selectedId < 0 && products.isNotEmpty()) selectedId = products.first().id
    }
    LaunchedEffect(selectedId, business.id) {
        val p = if (selectedId >= 0) c.repo.getProduct(business.id, selectedId) else null
        selected = p
        if (p != null) {
            batch = p.product.batchNumber
            manualExpiry = null
            design = designForProduct(c, p.product.defaultDesignId, business.defaultLabelSizeId)
        }
    }
    LaunchedEffect(printers) {
        if (printers.none { it.id == printerId }) printerId = printers.firstOrNull()?.id ?: -1L
    }

    val product = selected?.product
    val resolved = product?.let {
        DateEngine.resolve(LocalDate.parse(mfg), null, it.shelfLife, ShelfLifeUnit.parse(it.shelfUnit), manualExpiry?.let { e -> LocalDate.parse(e) })
    }
    val data = if (product != null && resolved != null)
        LabelDataFactory.build(business, product, selected?.barcode, resolved, batch, language) else Tokens.SAMPLE
    val printer = printers.firstOrNull { it.id == printerId }
    val datesOk = resolved?.expiry != null || "exp" in LabelDataFactory.hiddenSet(product?.hiddenFields.orEmpty())

    fun item() = BatchItem(productId = product!!.id, copies = copies, mfgDate = mfg, manualExpiry = manualExpiry,
        batch = batch, designId = design?.id?.takeIf { it > 0 }, labelSizeId = business.defaultLabelSizeId, language = language)

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null && product != null) scope.launch {
            error = runCatching {
                withContext(Dispatchers.IO) {
                    val labels = c.printQueue.prepare(business.id, listOf(item()))
                    context.contentResolver.openOutputStream(uri)?.use { Output.writePdf(labels, it) }
                }
            }.exceptionOrNull()?.message
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Quick Print", "${business.name} · ${design?.let { "${it.widthMm.toInt()} × ${it.heightMm.toInt()} mm" } ?: ""} · ${printer?.dpi ?: 203} DPI", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionLabel("1 · Product")
            Field(query, { query = it }, "Search products")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                products.take(40).forEach { p ->
                    val on = p.id == selectedId
                    Surface(onClick = { selectedId = p.id; jobId = null }, shape = RoundedCornerShape(16.dp),
                        color = if (on) Fls.Chocolate else Fls.Card, border = BorderStroke(if (on) 2.dp else 1.dp, if (on) Fls.Chocolate else Fls.Line),
                        modifier = Modifier.heightIn(min = 64.dp).widthIn(min = 96.dp)) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            Text(p.nameEn, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (on) Fls.Ivory else Fls.Ink, maxLines = 1)
                            if (p.nameUr.isNotBlank()) UrduText(p.nameUr, size = 13, color = if (on) Fls.Ivory else Fls.Ink)
                        }
                    }
                }
                if (products.isEmpty()) TextButton(onClick = { onNavigate(Routes.product(0)) }) { Text("Add your first product") }
            }

            SectionLabel("2 · Preview at actual size ratio")
            LabelPreview(design, data, printer?.dpi ?: 203, onRendered = { warnings = it.warnings })
            warnings.forEach { Notice(it, NoticeKind.Warning) }
            Segmented(listOf("EN", "UR", "BOTH"), language, { when (it) { "EN" -> "English"; "UR" -> "اردو"; else -> "Both" } }, { language = it })
            if (product != null) TextButton(onClick = { onNavigate(Routes.templates(product.id)) }) {
                Icon(Icons.Outlined.Dashboard, null); Text("  Change label design for ${product.nameEn}")
            }

            CardBlock {
                SectionLabel("3 · Dates")
                Surface(onClick = { pickMfg = true }, color = Fls.Card) {
                    InfoRow("Manufactured (tap to change)", DateEngine.format(LocalDate.parse(mfg), fmt) + if (mfg == LocalDate.now().toString()) " · Today" else "")
                }
                InfoRow("Shelf life (your setting)", DateEngine.describeShelfLife(product?.shelfLife, ShelfLifeUnit.parse(product?.shelfUnit)))
                Surface(onClick = { pickExp = true }, color = Fls.Card) {
                    InfoRow("Expiry (tap to pick by hand)",
                        (resolved?.expiry?.let { DateEngine.format(it, fmt) } ?: "—") + if (resolved?.expiryIsManual == true) " · Manual" else if (resolved?.expiry != null) " · Auto" else "")
                }
                if (manualExpiry != null) TextButton(onClick = { manualExpiry = null }) { Text("Use the automatic expiry date") }
                resolved?.warning?.let { Notice(it, NoticeKind.Warning) }
                Field(batch, { batch = it }, "Batch number (optional)")
            }

            CardBlock {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Copies", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Stepper(copies, { copies = it }, 1, 999)
                }
                HorizontalDivider(color = Fls.LineSoft)
                SectionLabel("Printer")
                PrinterPicker(printers, printerId, { printerId = it }, { onNavigate(Routes.PRINTERS) })
            }
            error?.let { Notice(it, NoticeKind.Error) }
            jobId?.let { PrintStatusCard(it) { jobId = null } }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Print $copies label${if (copies == 1) "" else "s"}", {
                error = null
                val p = printer ?: return@PrimaryButton
                scope.launch {
                    when (val r = startPrint(context, c, business.id, p, listOf(item()))) {
                        is PrintStart.Started -> jobId = r.jobId
                        is PrintStart.Error -> error = r.message
                    }
                }
            }, enabled = product != null && printer != null && datesOk,
                modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.Print, height = 60.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Add to batch", {
                    BatchCart.items.add(item()); onNavigate(Routes.BATCH)
                }, enabled = product != null, modifier = Modifier.weight(1f), icon = Icons.Outlined.Add)
                SecondaryButton("PDF", { pdfLauncher.launch("${product?.nameEn ?: "label"}.pdf") }, enabled = product != null,
                    modifier = Modifier.weight(1f), icon = Icons.Outlined.PictureAsPdf)
            }
            if (product != null && !datesOk)
                Text("Set a shelf life or pick an expiry date to print.", style = MaterialTheme.typography.bodySmall, color = Fls.Danger)
        }
    }
    if (pickMfg) DatePickDialog(LocalDate.parse(mfg), { mfg = it.toString() }, { pickMfg = false })
    if (pickExp) DatePickDialog(resolved?.expiry ?: LocalDate.parse(mfg), { manualExpiry = it.toString() }, { pickExp = false })
}
