package com.foodlabel.studio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodlabel.studio.data.db.PrintJobEntity
import com.foodlabel.studio.data.db.ProductEntity
import com.foodlabel.studio.dates.DateEngine
import com.foodlabel.studio.dates.DateFormatOption
import com.foodlabel.studio.dates.ShelfLifeUnit
import com.foodlabel.studio.printing.BatchItem
import com.foodlabel.studio.printing.JobStatus
import com.foodlabel.studio.printing.Output
import com.foodlabel.studio.ui.BatchCart
import com.foodlabel.studio.ui.LocalBusiness
import com.foodlabel.studio.ui.LocalContainer
import com.foodlabel.studio.ui.Routes
import com.foodlabel.studio.ui.components.CardBlock
import com.foodlabel.studio.ui.components.Chip
import com.foodlabel.studio.ui.components.DatePickDialog
import com.foodlabel.studio.ui.components.EmptyState
import com.foodlabel.studio.ui.components.Field
import com.foodlabel.studio.ui.components.Notice
import com.foodlabel.studio.ui.components.NoticeKind
import com.foodlabel.studio.ui.components.PrimaryButton
import com.foodlabel.studio.ui.components.ScreenHeader
import com.foodlabel.studio.ui.components.SecondaryButton
import com.foodlabel.studio.ui.components.SectionLabel
import com.foodlabel.studio.ui.components.Stepper
import com.foodlabel.studio.ui.theme.Fls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Several products, each with its own quantity, dates and batch number, printed as one job. */
@Composable
fun BatchScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val c = LocalContainer.current
    val business = LocalBusiness.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fmt = DateFormatOption.parse(business.dateFormat)
    val items = BatchCart.items
    val printers by remember(business.id) { c.repo.printers(business.id) }.collectAsStateWithLifecycle(emptyList())
    val saved by remember(business.id) { c.repo.savedBatches(business.id) }.collectAsStateWithLifecycle(emptyList())
    val allProducts by remember(business.id) { c.repo.searchProducts(business.id, "", null, false) }.collectAsStateWithLifecycle(emptyList())
    val byId = allProducts.associateBy { it.id }

    var printerId by rememberSaveable { mutableStateOf(-1L) }
    var picking by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var jobId by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var dateFor by remember { mutableStateOf<Pair<Int, Boolean>?>(null) } // index, isExpiry

    LaunchedEffect(printers) { if (printers.none { it.id == printerId }) printerId = printers.firstOrNull()?.id ?: -1L }
    // Drop lines whose product belongs to another business or was deleted.
    LaunchedEffect(allProducts) { if (allProducts.isNotEmpty()) items.removeAll { it.productId !in byId } }

    val total = items.sumOf { it.copies }
    val printer = printers.firstOrNull { it.id == printerId }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null) scope.launch {
            error = runCatching {
                withContext(Dispatchers.IO) {
                    val labels = c.printQueue.prepare(business.id, items.toList())
                    context.contentResolver.openOutputStream(uri)?.use { Output.writePdf(labels, it) }
                }
            }.exceptionOrNull()?.message
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Batch Printing", "${items.size} products · $total labels", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (saved.isNotEmpty()) {
                SectionLabel("Saved batches")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    saved.forEach { s ->
                        Chip(s.savedName ?: "Batch", false, {
                            items.clear()
                            items.addAll(c.printQueue.decodeItems(s.payload).map { it.copy(mfgDate = LocalDate.now().toString(), manualExpiry = null) })
                        })
                    }
                }
            }
            if (items.isEmpty()) EmptyState("No products in this batch", "Add the products you want to print together. Each one keeps its own quantity, dates and batch number.")
            items.forEachIndexed { i, item ->
                val p: ProductEntity? = byId[item.productId]
                val resolved = p?.let {
                    DateEngine.resolve(LocalDate.parse(item.mfgDate), null, it.shelfLife, ShelfLifeUnit.parse(it.shelfUnit), item.manualExpiry?.let { e -> LocalDate.parse(e) })
                }
                CardBlock {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p?.nameEn ?: "Loading…", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton({ items.removeAt(i) }) { Icon(Icons.Outlined.Delete, "Remove from batch", tint = Fls.Danger) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Labels", Modifier.weight(1f))
                        Stepper(item.copies, { items[i] = item.copy(copies = it) }, 1, 999)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("MFG ${DateEngine.format(LocalDate.parse(item.mfgDate), fmt)}", { dateFor = i to false }, modifier = Modifier.weight(1f))
                        SecondaryButton("EXP ${resolved?.expiry?.let { DateEngine.format(it, fmt) } ?: "set"}" + if (item.manualExpiry != null) " ✎" else "",
                            { dateFor = i to true }, modifier = Modifier.weight(1f))
                    }
                    if (item.manualExpiry != null) TextButton({ items[i] = item.copy(manualExpiry = null) }) { Text("Use the automatic expiry date") }
                    resolved?.warning?.let { Notice(it, NoticeKind.Warning) }
                    Field(item.batch.orEmpty(), { items[i] = item.copy(batch = it) }, "Batch number (optional)")
                }
            }
            SecondaryButton("Add products", { picking = true }, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.Add)
            if (items.isNotEmpty()) CardBlock {
                SectionLabel("Printer")
                PrinterPicker(printers, printerId, { printerId = it }, { onNavigate(Routes.PRINTERS) })
            }
            error?.let { Notice(it, NoticeKind.Error) }
            jobId?.let { PrintStatusCard(it) { jobId = null } }
        }
        if (items.isNotEmpty()) Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Print all · $total labels", {
                error = null
                val pr = printer ?: return@PrimaryButton
                scope.launch {
                    when (val r = startPrint(context, c, business.id, pr, items.toList())) {
                        is PrintStart.Started -> jobId = r.jobId
                        is PrintStart.Error -> error = r.message
                    }
                }
            }, enabled = printer != null, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.Print, height = 60.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Save batch", { naming = true }, modifier = Modifier.weight(1f))
                SecondaryButton("PDF", { pdfLauncher.launch("labels-batch.pdf") }, modifier = Modifier.weight(1f))
                SecondaryButton("Clear", { items.clear() }, modifier = Modifier.weight(1f))
            }
        }
    }

    dateFor?.let { (i, isExp) ->
        val item = items.getOrNull(i)
        if (item != null) DatePickDialog(
            initial = LocalDate.parse(if (isExp) item.manualExpiry ?: item.mfgDate else item.mfgDate),
            onPick = { d -> if (i < items.size) items[i] = if (isExp) item.copy(manualExpiry = d.toString()) else item.copy(mfgDate = d.toString()) },
            onDismiss = { dateFor = null },
        )
    }

    if (picking) {
        var q by remember { mutableStateOf("") }
        var chosen by remember { mutableStateOf(setOf<Long>()) }
        AlertDialog(onDismissRequest = { picking = false }, containerColor = Fls.Card,
            title = { Text("Add products") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(q, { q = it }, "Search")
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        lazyItems(allProducts.filter { q.isBlank() || it.nameEn.contains(q, true) || it.nameUr.contains(q) || it.code.contains(q, true) }, key = { it.id }) { p ->
                            Surface(onClick = { chosen = if (p.id in chosen) chosen - p.id else chosen + p.id }, color = Fls.Card) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(p.id in chosen, { on -> chosen = if (on) chosen + p.id else chosen - p.id })
                                    Text(p.nameEn, Modifier.weight(1f))
                                    Text(DateEngine.describeShelfLife(p.shelfLife, ShelfLifeUnit.parse(p.shelfUnit)), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(enabled = chosen.isNotEmpty(), onClick = {
                val today = LocalDate.now().toString()
                chosen.forEach { id -> items.add(BatchItem(productId = id, copies = 1, mfgDate = today, batch = byId[id]?.batchNumber, labelSizeId = business.defaultLabelSizeId)) }
                picking = false
            }) { Text("Add ${chosen.size}", fontWeight = FontWeight.Bold, color = Fls.Chocolate) } },
            dismissButton = { TextButton({ picking = false }) { Text("Cancel", color = Fls.Muted) } })
    }

    if (naming) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { naming = false }, containerColor = Fls.Card,
            title = { Text("Save this batch") },
            text = { Field(name, { name = it }, "Name, e.g. Morning bakery run") },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
                val payload = Json.encodeToString(ListSerializer(BatchItem.serializer()), items.toList())
                scope.launch {
                    c.repo.insertJob(PrintJobEntity(businessId = business.id, printerId = null, printerName = "", status = JobStatus.SAVED.name,
                        totalLabels = total, payload = payload, savedName = name.trim()), emptyList())
                    naming = false
                }
            }) { Text("Save", fontWeight = FontWeight.Bold, color = Fls.Chocolate) } },
            dismissButton = { TextButton({ naming = false }) { Text("Cancel", color = Fls.Muted) } })
    }
}
