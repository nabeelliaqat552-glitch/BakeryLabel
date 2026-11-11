package com.foodlabel.studio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodlabel.studio.dates.DateEngine
import com.foodlabel.studio.dates.ShelfLifeUnit
import com.foodlabel.studio.label.LabelDataFactory
import com.foodlabel.studio.ui.LocalBusiness
import com.foodlabel.studio.ui.LocalContainer
import com.foodlabel.studio.ui.Routes
import com.foodlabel.studio.ui.components.Chip
import com.foodlabel.studio.ui.components.ChipRow
import com.foodlabel.studio.ui.components.EmptyState
import com.foodlabel.studio.ui.components.Field
import com.foodlabel.studio.ui.components.Notice
import com.foodlabel.studio.ui.components.NoticeKind
import com.foodlabel.studio.ui.components.ScreenHeader
import com.foodlabel.studio.ui.components.UrduText
import com.foodlabel.studio.ui.theme.Fls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProductsScreen(initialCategory: Long?, onNavigate: (String) -> Unit) {
    val c = LocalContainer.current
    val business = LocalBusiness.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(initialCategory ?: -1L) }
    var favouritesOnly by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, NoticeKind>?>(null) }

    val categories by remember(business.id) { c.repo.categories(business.id) }.collectAsStateWithLifecycle(emptyList())
    val products by remember(business.id, query, categoryId, favouritesOnly) {
        c.repo.searchProducts(business.id, query, categoryId.takeIf { it >= 0 }, favouritesOnly)
    }.collectAsStateWithLifecycle(emptyList())
    val catNames = categories.associate { it.id to it.name }

    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) scope.launch {
            message = runCatching {
                withContext(Dispatchers.IO) {
                    val csv = c.backup.productsCsv(business.id)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                "Products exported." to NoticeKind.Success
            }.getOrElse { (it.message ?: "Export failed.") to NoticeKind.Error }
        }
    }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            message = runCatching {
                val r = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { c.backup.importCsv(business.id, it) } }
                val details = r.messages.take(5).joinToString("\n")
                ("Imported ${r.added} products" + (if (r.skipped > 0) ", skipped ${r.skipped}" else "") + "." +
                    if (details.isNotEmpty()) "\n$details" else "") to (if (r.messages.isEmpty()) NoticeKind.Success else NoticeKind.Warning)
            }.getOrElse { (it.message ?: "Import failed.") to NoticeKind.Error }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("My Products", "${business.name} · ${products.size} shown") {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.ImportExport, "Import or export products") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Export products (CSV)") }, onClick = { menu = false; exportCsv.launch("${business.name}-products.csv") })
                        DropdownMenuItem(text = { Text("Import products (CSV)") }, onClick = { menu = false; importCsv.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel")) })
                    }
                }
            }
            Box(Modifier.padding(horizontal = 16.dp)) { Field(query, { query = it }, "Search name, code or barcode") }
            ChipRow(Modifier.padding(vertical = 10.dp)) {
                Chip("All", categoryId < 0 && !favouritesOnly, { categoryId = -1; favouritesOnly = false })
                Chip("Favourites", favouritesOnly, { favouritesOnly = !favouritesOnly })
                categories.forEach { cat -> Chip(cat.name, cat.id == categoryId, { categoryId = cat.id; favouritesOnly = false }) }
                Chip("+ Category", false, { onNavigate(Routes.CATEGORIES) })
            }
            message?.let { Box(Modifier.padding(horizontal = 16.dp)) { Notice(it.first, it.second) } }
            if (products.isEmpty()) EmptyState(
                if (query.isBlank()) "No products here yet" else "No product matches \"$query\"",
                "Add a product with its name in English and Urdu, its price and shelf life.", "Add product",
            ) { onNavigate(Routes.product(0)) }
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(products, key = { it.id }) { p ->
                    Surface(onClick = { onNavigate(Routes.product(p.id)) }, shape = RoundedCornerShape(18.dp), color = Fls.Card,
                        border = BorderStroke(1.dp, Fls.Line), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Fls.Sand), contentAlignment = Alignment.Center) {
                                Text(p.nameEn.take(1).uppercase(), fontFamily = Fls.Display, fontSize = 20.sp, color = Fls.GoldDeep)
                            }
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.nameEn, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    if (p.nameUr.isNotBlank()) UrduText(p.nameUr, size = 13)
                                }
                                Text(
                                    listOfNotNull(p.categoryId?.let { catNames[it] }, DateEngine.describeShelfLife(p.shelfLife, ShelfLifeUnit.parse(p.shelfUnit)),
                                        p.price?.let { "${business.currencySymbol} ${LabelDataFactory.formatPrice(it)}" }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                )
                            }
                            IconButton(onClick = { onNavigate(Routes.quick(p.id)) }) { Icon(Icons.Outlined.Print, "Print ${p.nameEn}", tint = Fls.Chocolate) }
                            IconButton(onClick = { scope.launch { c.repo.setFavourite(business.id, p.id, !p.favourite) } }) {
                                Icon(if (p.favourite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                    if (p.favourite) "Remove from favourites" else "Add to favourites", tint = Fls.GoldRing)
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onNavigate(Routes.product(0)) }, containerColor = Fls.Chocolate, contentColor = Fls.Ivory,
            shape = RoundedCornerShape(28.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(Icons.Outlined.Add, null, tint = Fls.Gold) }, text = { Text("Add product", fontWeight = FontWeight.Bold) },
        )
    }
}

