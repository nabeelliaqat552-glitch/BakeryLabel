package com.foodlabel.studio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodlabel.studio.barcode.BarcodeCheck
import com.foodlabel.studio.barcode.BarcodeRules
import com.foodlabel.studio.barcode.BarcodeType
import com.foodlabel.studio.data.db.ProductEntity
import com.foodlabel.studio.dates.DateEngine
import com.foodlabel.studio.dates.DateFormatOption
import com.foodlabel.studio.dates.ShelfLifeUnit
import com.foodlabel.studio.label.ElementType
import com.foodlabel.studio.label.LabelData
import com.foodlabel.studio.label.LabelDataFactory
import com.foodlabel.studio.label.LabelDesign
import com.foodlabel.studio.label.LabelElement
import com.foodlabel.studio.ui.LocalBusiness
import com.foodlabel.studio.ui.LocalContainer
import com.foodlabel.studio.ui.Routes
import com.foodlabel.studio.ui.components.BarcodeScannerDialog
import com.foodlabel.studio.ui.components.CardBlock
import com.foodlabel.studio.ui.components.Chip
import com.foodlabel.studio.ui.components.ConfirmDialog
import com.foodlabel.studio.ui.components.Field
import com.foodlabel.studio.ui.components.InfoRow
import com.foodlabel.studio.ui.components.LabelPreview
import com.foodlabel.studio.ui.components.Notice
import com.foodlabel.studio.ui.components.NoticeKind
import com.foodlabel.studio.ui.components.PrimaryButton
import com.foodlabel.studio.ui.components.RoundIconButton
import com.foodlabel.studio.ui.components.ScreenHeader
import com.foodlabel.studio.ui.components.SecondaryButton
import com.foodlabel.studio.ui.components.SectionLabel
import com.foodlabel.studio.ui.components.Segmented
import com.foodlabel.studio.ui.components.Stepper
import com.foodlabel.studio.ui.components.ToggleRow
import com.foodlabel.studio.ui.theme.Fls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

private val FIELD_LABELS = mapOf(
    "name_ur" to "Urdu name", "mfg" to "Manufacturing date", "exp" to "Expiry date", "price" to "Price",
    "weight" to "Weight", "batch" to "Batch number", "barcode" to "Barcode", "code" to "Product code",
    "business" to "Business name", "logo" to "Logo",
)

@Composable
fun ProductEditScreen(productId: Long, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val c = LocalContainer.current
    val business = LocalBusiness.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories by remember(business.id) { c.repo.categories(business.id) }.collectAsStateWithLifecycle(emptyList())

    var loaded by remember { mutableStateOf(productId == 0L) }
    var original by remember { mutableStateOf<ProductEntity?>(null) }
    var nameEn by remember { mutableStateOf("") }
    var nameUr by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var code by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var batch by remember { mutableStateOf("") }
    var shelfSet by remember { mutableStateOf(false) }
    var shelf by remember { mutableStateOf(1) }
    var unit by remember { mutableStateOf(ShelfLifeUnit.DAYS) }
    var hidden by remember { mutableStateOf(setOf<String>()) }
    var imagePath by remember { mutableStateOf<String?>(null) }
    var barcodeType by remember { mutableStateOf(BarcodeType.EAN_13) }
    var barcode by remember { mutableStateOf("") }
    var barcodeInternal by remember { mutableStateOf(false) }
    val custom = remember { mutableStateListOf<Pair<String, String>>() }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scanNote by remember { mutableStateOf<Pair<String, NoticeKind>?>(null) }

    LaunchedEffect(productId, business.id) {
        if (productId == 0L) return@LaunchedEffect
        val pw = c.repo.getProduct(business.id, productId) ?: run { error = "This product is not in ${business.name}."; return@LaunchedEffect }
        val p = pw.product
        original = p; nameEn = p.nameEn; nameUr = p.nameUr; categoryId = p.categoryId; code = p.code
        price = p.price?.let { LabelDataFactory.formatPrice(it).replace(",", "") }.orEmpty(); weight = p.weight; batch = p.batchNumber
        shelfSet = p.shelfLife != null; shelf = p.shelfLife ?: 1; unit = ShelfLifeUnit.parse(p.shelfUnit)
        hidden = LabelDataFactory.hiddenSet(p.hiddenFields); imagePath = p.imagePath
        pw.barcode?.let { barcodeType = BarcodeType.parse(it.type); barcode = it.value; barcodeInternal = it.isInternal }
        custom.clear(); custom.addAll(LabelDataFactory.parseCustom(p.customFields).toList())
        loaded = true
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            imagePath = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "products").apply { mkdirs() }
                val f = File(dir, "p_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                f.absolutePath
            }
        }
    }

    val check = if (barcode.isBlank()) null else BarcodeRules.validate(barcodeType, barcode)
    val fmt = DateFormatOption.parse(business.dateFormat)
    val today = LocalDate.now()
    val expiryExample = if (shelfSet) DateEngine.format(DateEngine.calculateExpiry(today, shelf, unit), fmt) else "—"

    fun save(after: (Long) -> Unit) {
        error = null
        if (nameEn.isBlank()) { error = "Enter the product name in English."; return }
        val priceValue = price.replace(",", "").trim().let { if (it.isEmpty()) null else it.toDoubleOrNull() }
        if (price.isNotBlank() && priceValue == null) { error = "Price must be a number, e.g. 320 or 99.50"; return }
        if (check is BarcodeCheck.Invalid) { error = check.reason; return }
        scope.launch {
            val value = (check as? BarcodeCheck.Valid)?.normalized
            if (value != null && c.repo.isBarcodeUsedElsewhere(business.id, value, original?.id ?: -1)) {
                error = "Another product in ${business.name} already uses barcode $value."; return@launch
            }
            val p = (original ?: ProductEntity(businessId = business.id, nameEn = "")).copy(
                businessId = business.id, nameEn = nameEn.trim(), nameUr = nameUr.trim(), categoryId = categoryId, code = code.trim(),
                price = priceValue, weight = weight.trim(), batchNumber = batch.trim(), shelfLife = if (shelfSet) shelf else null,
                shelfUnit = unit.name, hiddenFields = hidden.joinToString(","), imagePath = imagePath,
                customFields = LabelDataFactory.encodeCustom(custom.filter { it.first.isNotBlank() }.toMap()),
            )
            val id = c.repo.saveProduct(p, if (value != null) barcodeType else null, value, barcodeInternal)
            after(id)
        }
    }

    val barcodePreview = remember(barcodeType, check) {
        LabelDesign(name = "barcode", widthMm = 50.0, heightMm = 18.0, elements = listOf(
            LabelElement(type = ElementType.BARCODE, xMm = 3.0, yMm = 2.0, wMm = 44.0, hMm = 14.0, content = "{barcode}", fontPt = 7.0)))
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (productId == 0L) "New product" else "Edit product", business.name, onBack) {
            if (original != null) {
                RoundIconButton(Icons.Outlined.ContentCopy, "Duplicate product", {
                    scope.launch { c.repo.duplicateProduct(business.id, original!!.id)?.let { onNavigate(Routes.product(it)) } }
                })
            }
        }
        if (!loaded) { error?.let { Notice(it, NoticeKind.Error, Modifier.padding(16.dp)) }; return@Column }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CardBlock {
                SectionLabel("Names")
                Field(nameEn, { nameEn = it }, "Name in English")
                Field(nameUr, { nameUr = it }, "اردو نام · Name in Urdu", rtl = true)
                SectionLabel("Category")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("None", categoryId == null, { categoryId = null })
                    categories.forEach { cat -> Chip(cat.name, cat.id == categoryId, {
                        categoryId = cat.id
                        if (!shelfSet && cat.defaultShelfLife != null) { shelfSet = true; shelf = cat.defaultShelfLife; unit = ShelfLifeUnit.parse(cat.defaultShelfUnit) }
                    }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(code, { code = it }, "Product code", Modifier.weight(1f))
                    Field(price, { price = it }, "Price (${business.currencySymbol})", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(weight, { weight = it }, "Weight, e.g. 200 g", Modifier.weight(1f))
                    Field(batch, { batch = it }, "Default batch", Modifier.weight(1f))
                }
            }

            CardBlock {
                SectionLabel("Shelf life & expiry")
                ToggleRow("This product has a shelf life", shelfSet, { shelfSet = it })
                if (shelfSet) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stepper(shelf, { shelf = it }, 1, 999)
                    }
                    Segmented(ShelfLifeUnit.entries, unit, { it.label }, { unit = it }, Modifier.fillMaxWidth())
                    InfoRow("Made today", DateEngine.format(today, fmt))
                    InfoRow("Would expire", expiryExample)
                }
                Text("You decide the shelf life. The app only does the date maths and never suggests a food-safety period. " +
                    "An expiry date picked by hand when printing is never changed automatically.", style = MaterialTheme.typography.bodySmall)
            }

            CardBlock {
                SectionLabel("Barcode")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BarcodeType.entries.forEach { t -> Chip(t.label, t == barcodeType, { barcodeType = t; barcodeInternal = false }) }
                }
                Field(barcode, { barcode = it; barcodeInternal = false; scanNote = null }, "Barcode number", keyboard = if (barcodeType in setOf(BarcodeType.CODE_128, BarcodeType.CODE_39, BarcodeType.QR_CODE)) KeyboardType.Text else KeyboardType.Number,
                    isError = check is BarcodeCheck.Invalid,
                    supporting = when (check) {
                        is BarcodeCheck.Invalid -> check.reason
                        is BarcodeCheck.Valid -> check.note ?: "Valid ${barcodeType.label}."
                        null -> "Leave empty for no barcode."
                    })
                PrimaryButton("Scan barcode with camera", { scanNote = null; scanning = true }, Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.QrCodeScanner, height = 52.dp)
                scanNote?.let { Notice(it.first, it.second) }
                SecondaryButton("Generate an internal code", {
                    scope.launch {
                        val v = c.repo.nextInternalBarcode(business.id)
                        barcodeType = BarcodeType.EAN_13; barcode = v; barcodeInternal = true
                    }
                }, modifier = Modifier.fillMaxWidth())
                if (barcodeInternal) Text("Internal in-store code (starts with 2). It is not an official GS1 retail barcode and is only for use inside your business.",
                    style = MaterialTheme.typography.bodySmall)
                if (check is BarcodeCheck.Valid) LabelPreview(barcodePreview, LabelData(barcode = check.normalized, barcodeType = barcodeType), 203)
            }

            CardBlock {
                SectionLabel("Show on label")
                FIELD_LABELS.forEach { (key, label) ->
                    ToggleRow(label, key !in hidden, { on -> hidden = if (on) hidden - key else hidden + key })
                }
            }

            CardBlock {
                SectionLabel("Custom fields")
                Text("Use them on a label with {custom:Name}, for example {custom:Ingredients}.", style = MaterialTheme.typography.bodySmall)
                custom.forEachIndexed { i, (k, v) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Field(k, { custom[i] = it to v }, "Name", Modifier.weight(1f))
                        Field(v, { custom[i] = k to it }, "Value", Modifier.weight(1.4f))
                        IconButton(onClick = { custom.removeAt(i) }) { Icon(Icons.Outlined.Delete, "Remove field", tint = Fls.Danger) }
                    }
                }
                TextButton(onClick = { custom.add("" to "") }) { Text("+ Add custom field") }
            }

            CardBlock {
                SectionLabel("Product photo (optional)")
                imagePath?.let { path ->
                    val bmp = remember(path) { android.graphics.BitmapFactory.decodeFile(path) }
                    if (bmp != null) Image(bmp.asImageBitmap(), "Product photo", contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(if (imagePath == null) "Add photo" else "Change photo", {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, modifier = Modifier.weight(1f))
                    if (imagePath != null) SecondaryButton("Remove", { imagePath = null }, modifier = Modifier.weight(1f))
                }
            }

            if (original != null) SecondaryButton("Delete product", { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), danger = true, icon = Icons.Outlined.Delete)
            error?.let { Notice(it, NoticeKind.Error) }
        }
        Row(Modifier.padding(16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Save & print", { save { id -> onNavigate(Routes.quick(id)) } }, modifier = Modifier.weight(1f), icon = Icons.Outlined.Print)
            PrimaryButton("Save product", { save { onBack() } }, modifier = Modifier.weight(1f))
        }
    }
    if (scanning) BarcodeScannerDialog(
        onResult = { code ->
            scanning = false
            barcodeType = code.type; barcode = code.value; barcodeInternal = false
            scope.launch {
                scanNote = if (c.repo.isBarcodeUsedElsewhere(business.id, code.value, original?.id ?: -1))
                    "Scanned ${code.type.label} ${code.value}, but another product in ${business.name} already uses this barcode." to NoticeKind.Warning
                else "Scanned ${code.type.label}: ${code.value}" to NoticeKind.Success
            }
        },
        onDismiss = { scanning = false },
    )
    if (confirmDelete) ConfirmDialog(
        "Delete ${original?.nameEn}?", "The product and its barcode are removed from ${business.name}. Print history is kept.",
        "Delete", { confirmDelete = false; scope.launch { original?.let { c.repo.deleteProduct(it) }; onBack() } }, { confirmDelete = false }, danger = true,
    )
}
