package com.foodlabel.studio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodlabel.studio.data.db.BusinessEntity
import com.foodlabel.studio.ui.LocalBusiness
import com.foodlabel.studio.ui.LocalContainer
import com.foodlabel.studio.ui.Routes
import com.foodlabel.studio.ui.components.CardBlock
import com.foodlabel.studio.ui.components.Field
import com.foodlabel.studio.ui.components.Gap
import com.foodlabel.studio.ui.components.PrimaryButton
import com.foodlabel.studio.ui.components.RoundIconButton
import com.foodlabel.studio.ui.components.SectionLabel
import com.foodlabel.studio.ui.components.ToggleRow
import com.foodlabel.studio.ui.theme.Fls
import kotlinx.coroutines.launch

private data class Tile(val label: String, val route: String, val icon: ImageVector)

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val business = LocalBusiness.current
    val repo = LocalContainer.current.repo
    val printers by remember(business.id) { repo.printers(business.id) }.collectAsStateWithLifecycle(emptyList())
    val sizes by remember(business.id) { repo.labelSizes(business.id) }.collectAsStateWithLifecycle(emptyList())
    val printer = printers.firstOrNull()
    val size = sizes.firstOrNull { it.id == business.defaultLabelSizeId }

    val tiles = listOf(
        Tile("Create New Label", Routes.templates(), Icons.Outlined.Add),
        Tile("My Products", Routes.products(), Icons.Outlined.Inventory2),
        Tile("Product Categories", Routes.CATEGORIES, Icons.Outlined.Category),
        Tile("Label Designer", Routes.designer(), Icons.Outlined.Edit),
        Tile("Template Library", Routes.templates(), Icons.Outlined.Dashboard),
        Tile("Saved Labels", Routes.DESIGN, Icons.Outlined.Bookmark),
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = { onNavigate(Routes.BUSINESSES) }, shape = RoundedCornerShape(28.dp), color = Fls.Card,
                border = BorderStroke(1.dp, Fls.Line)) {
                Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Fls.Chocolate), contentAlignment = Alignment.Center) {
                        Text(initials(business.name), color = Fls.GoldPale, fontFamily = Fls.Display, fontSize = 14.sp)
                    }
                    Text(business.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Icon(Icons.Outlined.ExpandMore, "Switch business", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            RoundIconButton(Icons.Outlined.Settings, "Settings", { onNavigate(Routes.SETTINGS) })
        }
        Column {
            SectionLabel("FoodLabel Studio")
            Text("What are we labelling today?", style = MaterialTheme.typography.headlineLarge)
        }
        Surface(onClick = { onNavigate(Routes.quick()) }, shape = RoundedCornerShape(22.dp), color = Fls.Chocolate, shadowElevation = 6.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Quick Print", fontFamily = Fls.Display, fontSize = 30.sp, color = Fls.Ivory)
                        Text("Pick a product. Print the label.", color = Fls.Sand, fontSize = 14.sp)
                    }
                    Box(Modifier.size(60.dp).clip(CircleShape).background(Fls.Gold), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Print, null, tint = Fls.Ink, modifier = Modifier.size(28.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (printer != null) Fls.Gold else Fls.Muted))
                    Text(
                        (printer?.name ?: "No printer set up yet") + (size?.let { " · ${it.name}" } ?: ""),
                        color = Fls.GoldPale, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    Surface(onClick = { onNavigate(t.route) }, shape = RoundedCornerShape(16.dp), color = Fls.Card,
                        border = BorderStroke(1.dp, Fls.Line), modifier = Modifier.weight(1f).height(100.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Icon(t.icon, null, tint = Fls.GoldDeep)
                            Text(t.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
        Surface(onClick = { onNavigate(Routes.SETTINGS) }, shape = RoundedCornerShape(18.dp), color = Fls.SandDeep,
            border = BorderStroke(1.dp, Fls.Line)) {
            Row(Modifier.padding(14.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Fls.Card).border(1.dp, Fls.Line, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Settings, null, tint = Fls.GoldDeep) }
                Column(Modifier.weight(1f)) {
                    Text("Settings & Tools", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Printers, label sizes, print history, backup, businesses", style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
            }
        }
        Gap(8)
    }
}

fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "FL" }

/** First run, or every business archived: create one (or bring one back). */
@Composable
fun OnboardingScreen() {
    val repo = LocalContainer.current.repo
    val scope = rememberCoroutineScope()
    val all by remember { repo.businesses(includeArchived = true) }.collectAsStateWithLifecycle(emptyList<BusinessEntity>())
    var name by rememberSaveable { mutableStateOf("") }
    var starter by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Gap(24)
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Fls.Chocolate), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Storefront, null, tint = Fls.Gold, modifier = Modifier.size(32.dp))
        }
        Text("Welcome to FoodLabel Studio", style = MaterialTheme.typography.headlineLarge)
        Text("Everything stays on this phone. No account and no internet needed.", color = Fls.Muted)
        CardBlock {
            SectionLabel("Your business")
            Field(name, { name = it }, "Business name, e.g. ABC Bakery")
            ToggleRow("Add starter products", starter, { starter = it },
                "Bakery, sweets, dairy and fast-food items you can edit or delete. No shelf lives are filled in.")
        }
        PrimaryButton("Create business", {
            busy = true
            scope.launch { repo.createBusiness(BusinessEntity(name = name.trim()), starter); busy = false }
        }, enabled = name.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth())
        if (all.isNotEmpty()) {
            SectionLabel("Or open an existing business")
            all.forEach { b ->
                Surface(onClick = { scope.launch { if (b.archived) repo.setArchived(b.id, false); repo.setActive(b.id) } },
                    shape = RoundedCornerShape(16.dp), color = Fls.Card, border = BorderStroke(1.dp, Fls.Line), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (b.archived) Text("Archived · tap to restore", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}
