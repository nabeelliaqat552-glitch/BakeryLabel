package com.foodlabel.studio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodlabel.studio.data.db.CategoryEntity
import com.foodlabel.studio.dates.ShelfLifeUnit
import com.foodlabel.studio.ui.LocalBusiness
import com.foodlabel.studio.ui.LocalContainer
import com.foodlabel.studio.ui.Routes
import com.foodlabel.studio.ui.components.CATEGORY_ICON_KEYS
import com.foodlabel.studio.ui.components.CATEGORY_TINTS
import com.foodlabel.studio.ui.components.ConfirmDialog
import com.foodlabel.studio.ui.components.Field
import com.foodlabel.studio.ui.components.ScreenHeader
import com.foodlabel.studio.ui.components.SectionLabel
import com.foodlabel.studio.ui.components.Segmented
import com.foodlabel.studio.ui.components.Stepper
import com.foodlabel.studio.ui.components.ToggleRow
import com.foodlabel.studio.ui.components.UrduText
import com.foodlabel.studio.ui.components.categoryIcon
import com.foodlabel.studio.ui.theme.Fls
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoriesScreen(onNavigate: (String) -> Unit) {
    val c = LocalContainer.current
    val business = LocalBusiness.current
    val scope = rememberCoroutineScope()
    val cats by remember(business.id) { c.repo.categoriesWithCounts(business.id) }.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Categories", "${business.name} · ${cats.size} categories · tap to open, hold to edit")
        LazyVerticalGrid(
            GridCells.Fixed(2), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cats, key = { it.id }) { cat ->
                Column(
                    Modifier.heightIn(min = 128.dp).clip(RoundedCornerShape(20.dp)).background(Color(cat.tint))
                        .combinedClickable(
                            onClick = { onNavigate(Routes.products(cat.id)) },
                            onLongClick = { scope.launch { editing = c.repo.getCategory(business.id, cat.id) } },
                        ).padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xBFFFFDF8)), contentAlignment = Alignment.Center) {
                            Icon(categoryIcon(cat.iconKey), null, tint = Fls.Chocolate)
                        }
                        Box(Modifier.weight(1f))
                        IconButton(onClick = { scope.launch { editing = c.repo.getCategory(business.id, cat.id) } }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Edit, "Edit ${cat.name}", tint = Fls.Muted, modifier = Modifier.size(18.dp))
                        }
                    }
                    Column {
                        Text(cat.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Fls.Ink)
                        if (cat.nameUrdu.isNotBlank()) UrduText(cat.nameUrdu, size = 13, color = Color(0xFF4A3324))
                        Text("${cat.productCount} items", fontSize = 12.sp, color = Color(0xFF4A3324), fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Box(
                    Modifier.heightIn(min = 128.dp).clip(RoundedCornerShape(20.dp)).border(1.5.dp, Fls.GoldRing, RoundedCornerShape(20.dp))
                        .clickable { editing = CategoryEntity(businessId = business.id, name = "") },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Add, null, tint = Fls.Chocolate)
                        Text("Add category", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    editing?.let { cat -> CategoryDialog(cat, onDismiss = { editing = null }) }
}

@Composable
private fun CategoryDialog(initial: CategoryEntity, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var ur by remember { mutableStateOf(initial.nameUrdu) }
    var icon by remember { mutableStateOf(initial.iconKey) }
    var tint by remember { mutableStateOf(initial.tint) }
    var hasShelf by remember { mutableStateOf(initial.defaultShelfLife != null) }
    var shelf by remember { mutableStateOf(initial.defaultShelfLife ?: 1) }
    var unit by remember { mutableStateOf(ShelfLifeUnit.parse(initial.defaultShelfUnit)) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Fls.Card,
        title = { Text(if (initial.id == 0L) "New category" else "Edit category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(name, { name = it }, "Name")
                Field(ur, { ur = it }, "اردو نام · Urdu name", rtl = true)
                SectionLabel("Icon")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CATEGORY_ICON_KEYS.forEach { k ->
                        Surface(onClick = { icon = k }, shape = CircleShape, color = if (k == icon) Fls.Chocolate else Fls.Sand, modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(categoryIcon(k), k, tint = if (k == icon) Fls.Gold else Fls.Chocolate) }
                        }
                    }
                }
                SectionLabel("Colour")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CATEGORY_TINTS.forEach { t ->
                        Surface(onClick = { tint = t }, shape = CircleShape, color = Color(t),
                            border = BorderStroke(if (t == tint) 3.dp else 1.dp, if (t == tint) Fls.Chocolate else Fls.Line), modifier = Modifier.size(40.dp)) {}
                    }
                }
                ToggleRow("Default shelf life for new products", hasShelf, { hasShelf = it }, "You set it. The app never suggests one.")
                if (hasShelf) {
                    Stepper(shelf, { shelf = it }, 1, 999)
                    Segmented(ShelfLifeUnit.entries, unit, { it.label }, { unit = it }, Modifier.fillMaxWidth())
                }
                if (initial.id != 0L) TextButton(onClick = { confirmDelete = true }) { Text("Delete category", color = Fls.Danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                scope.launch {
                    c.repo.saveCategory(initial.copy(name = name.trim(), nameUrdu = ur.trim(), iconKey = icon, tint = tint,
                        defaultShelfLife = if (hasShelf) shelf else null, defaultShelfUnit = unit.name))
                    onDismiss()
                }
            }) { Text("Save", fontWeight = FontWeight.Bold, color = Fls.Chocolate) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Fls.Muted) } },
    )
    if (confirmDelete) ConfirmDialog(
        "Delete ${initial.name}?", "Products in this category are kept and become uncategorised.", "Delete",
        { scope.launch { c.repo.deleteCategory(initial); confirmDelete = false; onDismiss() } }, { confirmDelete = false }, danger = true,
    )
}
