package com.foodlabel.studio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodlabel.studio.data.db.BusinessEntity
import com.foodlabel.studio.dates.DateFormatOption
import com.foodlabel.studio.ui.LocalBusiness
import com.foodlabel.studio.ui.LocalContainer
import com.foodlabel.studio.ui.Routes
import com.foodlabel.studio.ui.components.CardBlock
import com.foodlabel.studio.ui.components.Chip
import com.foodlabel.studio.ui.components.Field
import com.foodlabel.studio.ui.components.Notice
import com.foodlabel.studio.ui.components.NoticeKind
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

@Composable
fun BusinessesScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val c = LocalContainer.current
    val active = LocalBusiness.current
    val scope = rememberCoroutineScope()
    val all by remember { c.repo.businesses(includeArchived = true) }.collectAsStateWithLifecycle(emptyList())
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf<BusinessEntity?>(null) }
    var duplicating by remember { mutableStateOf<BusinessEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("My Businesses", "Each business keeps its own products, labels and printers", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            all.forEach { b ->
                val on = b.id == active.id
                Surface(onClick = { if (!b.archived) scope.launch { c.repo.setActive(b.id) } }, shape = RoundedCornerShape(18.dp),
                    color = if (on) Fls.Chocolate else Fls.Card, border = BorderStroke(1.dp, if (on) Fls.Chocolate else Fls.Line), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(if (on) Fls.Gold else Fls.Sand), contentAlignment = Alignment.Center) {
                            Text(initials(b.name), fontFamily = Fls.Display, fontSize = 17.sp, color = if (on) Fls.Ink else Fls.GoldDeep)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(b.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (on) Fls.Ivory else Fls.Ink)
                            Text(when { b.archived -> "Archived · hidden, data kept"; on -> "In use now · ${b.currencyCode}"; else -> "Tap to switch · ${b.currencyCode}" },
                                fontSize = 12.sp, color = if (on) Fls.Sand else Fls.Muted)
                        }
                        Box {
                            IconButton(onClick = { menuFor = b.id }) { Icon(Icons.Outlined.MoreVert, "Options for ${b.name}", tint = if (on) Fls.Ivory else Fls.Ink) }
                            DropdownMenu(menuFor == b.id, { menuFor = null }) {
                                DropdownMenuItem({ Text("Edit") }, { menuFor = null; onNavigate(Routes.business(b.id)) })
                                DropdownMenuItem({ Text("Duplicate") }, { menuFor = null; duplicating = b })
                                DropdownMenuItem({ Text(if (b.archived) "Restore from archive" else "Archive") }, {
                                    menuFor = null; scope.launch { c.repo.setArchived(b.id, !b.archived) }
                                })
                                DropdownMenuItem({ Text("Delete", color = Fls.Danger) }, { menuFor = null; deleting = b })
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.padding(16.dp).navigationBarsPadding()) {
            PrimaryButton("Add business", { onNavigate(Routes.business(0)) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add)
        }
    }

    duplicating?.let { b ->
        var name by remember(b.id) { mutableStateOf(b.name + " copy") }
        AlertDialog(onDismissRequest = { duplicating = null }, containerColor = Fls.Card,
            title = { Text("Duplicate ${b.name}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Copies products, categories, label designs, sizes and printers into a new business. Print history is not copied.")
                Field(name, { name = it }, "New business name")
            } },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { scope.launch { c.repo.duplicateBusiness(b.id, name.trim()); duplicating = null } }) {
                Text("Duplicate", fontWeight = FontWeight.Bold, color = Fls.Chocolate) } },
            dismissButton = { TextButton({ duplicating = null }) { Text("Cancel", color = Fls.Muted) } })
    }
    deleting?.let { b ->
        var typed by remember(b.id) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { deleting = null }, containerColor = Fls.Card,
            title = { Text("Delete ${b.name}?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This permanently removes its products, label designs, printers and print history from this phone. " +
                    "Make a backup first if you may need them. To hide it without deleting, use Archive.")
                Field(typed, { typed = it }, "Type the business name to confirm")
            } },
            confirmButton = { TextButton(enabled = typed.trim().equals(b.name.trim(), ignoreCase = true), onClick = {
                scope.launch { c.repo.deleteBusiness(b.id); deleting = null }
            }) { Text("Delete forever", fontWeight = FontWeight.Bold, color = Fls.Danger) } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel", color = Fls.Muted) } })
    }
}

@Composable
fun BusinessEditScreen(businessId: Long, onBack: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var b by remember { mutableStateOf(BusinessEntity(name = "")) }
    var starter by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val sizes by remember(businessId) { c.repo.labelSizes(businessId) }.collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(businessId) { if (businessId != 0L) c.repo.getBusiness(businessId)?.let { b = it } }

    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { c.backup.saveLogo(it.readBytes()) } }
            b = b.copy(logoPath = path)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (businessId == 0L) "New business" else "Edit business", null, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CardBlock {
                SectionLabel("Business")
                Field(b.name, { b = b.copy(name = it) }, "Business name")
                Field(b.nameUrdu, { b = b.copy(nameUrdu = it) }, "اردو نام · Name in Urdu", rtl = true)
                Field(b.address, { b = b.copy(address = it) }, "Address (optional)", singleLine = false)
                Field(b.phone, { b = b.copy(phone = it) }, "Phone (optional)")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    b.logoPath?.let { path ->
                        val bmp = remember(path) { android.graphics.BitmapFactory.decodeFile(path) }
                        if (bmp != null) Image(bmp.asImageBitmap(), "Logo", contentScale = ContentScale.Fit, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                    }
                    SecondaryButton(if (b.logoPath == null) "Add logo" else "Change logo", { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                    if (b.logoPath != null) TextButton({ b = b.copy(logoPath = null) }) { Text("Remove", color = Fls.Danger) }
                }
                Text("Logos print in black and white. A simple dark logo on a white background works best.", style = MaterialTheme.typography.bodySmall)
            }
            CardBlock {
                SectionLabel("Money & dates")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(b.currencySymbol, { b = b.copy(currencySymbol = it) }, "Currency symbol", Modifier.weight(1f))
                    Field(b.currencyCode, { b = b.copy(currencyCode = it.uppercase().take(3)) }, "Code, e.g. PKR", Modifier.weight(1f))
                }
                Text("Date format on labels", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateFormatOption.entries.forEach { f -> Chip(f.label, b.dateFormat == f.name, { b = b.copy(dateFormat = f.name) }) }
                }
                ToggleRow("Print manufacturing time", b.showMfgTime, { b = b.copy(showMfgTime = it) }, "Adds the time of printing after the date.")
                Text("Label language", fontWeight = FontWeight.Bold)
                Segmented(listOf("EN", "UR", "BOTH"), b.labelLanguage, { when (it) { "EN" -> "English"; "UR" -> "اردو"; else -> "Both" } }, { b = b.copy(labelLanguage = it) }, Modifier.fillMaxWidth())
            }
            if (businessId != 0L) CardBlock {
                SectionLabel("Default label size")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sizes.forEach { s -> Chip(s.name, b.defaultLabelSizeId == s.id, { b = b.copy(defaultLabelSizeId = s.id) }) }
                }
            } else CardBlock {
                ToggleRow("Add starter products", starter, { starter = it }, "Common bakery, sweets, dairy and fast-food items, all editable. No shelf lives are filled in.")
            }
            error?.let { Notice(it, NoticeKind.Error) }
        }
        Box(Modifier.padding(16.dp).navigationBarsPadding()) {
            PrimaryButton(if (businessId == 0L) "Create business" else "Save", {
                if (b.name.isBlank()) { error = "Enter the business name."; return@PrimaryButton }
                scope.launch {
                    if (businessId == 0L) c.repo.createBusiness(b.copy(name = b.name.trim()), starter) else c.repo.updateBusiness(b.copy(name = b.name.trim()))
                    onBack()
                }
            }, Modifier.fillMaxWidth())
        }
    }
}
