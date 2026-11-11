package com.foodlabel.studio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodlabel.studio.core.Units
import com.foodlabel.studio.data.seed.TemplateLibrary
import com.foodlabel.studio.label.LabelDesign
import com.foodlabel.studio.label.Tokens
import com.foodlabel.studio.ui.LocalBusiness
import com.foodlabel.studio.ui.LocalContainer
import com.foodlabel.studio.ui.Routes
import com.foodlabel.studio.ui.components.Chip
import com.foodlabel.studio.ui.components.ChipRow
import com.foodlabel.studio.ui.components.ConfirmDialog
import com.foodlabel.studio.ui.components.EmptyState
import com.foodlabel.studio.ui.components.LabelPreview
import com.foodlabel.studio.ui.components.PrimaryButton
import com.foodlabel.studio.ui.components.ScreenHeader
import com.foodlabel.studio.ui.theme.Fls
import kotlinx.coroutines.launch

fun sizeText(d: LabelDesign) = (if (d.round) "Round " else "") + "${Units.formatMm(d.widthMm)} × ${Units.formatMm(d.heightMm)} mm"

@Composable
private fun DesignCard(d: LabelDesign, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = Fls.Card, border = BorderStroke(1.dp, Fls.Line)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LabelPreview(d, Tokens.SAMPLE, 203)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text(sizeText(d), style = MaterialTheme.typography.bodySmall)
                }
                trailing()
            }
        }
    }
}

/**
 * Template library. With [pickFor] set, choosing a template makes it that product's label design;
 * otherwise it opens in the designer as an editable copy.
 */
@Composable
fun TemplatesScreen(pickFor: Long?, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val c = LocalContainer.current
    val business = LocalBusiness.current
    val scope = rememberCoroutineScope()
    val templates by remember(business.id) { c.repo.templates(business.id) }.collectAsStateWithLifecycle(emptyList())
    val saved by remember(business.id) { c.repo.savedDesigns(business.id) }.collectAsStateWithLifecycle(emptyList())
    var filter by rememberSaveable { mutableStateOf("All") }

    val shown = when (filter) {
        "All" -> templates
        "Small thermal" -> templates.filter { it.heightMm <= 20.0 }
        "My templates" -> templates.filter { it.businessId != null }
        "Saved labels" -> saved
        else -> templates.filter { it.groupName == filter }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (pickFor != null) "Choose a label design" else "Template Library",
            "${templates.count { it.businessId == null }} ready-made · ${templates.count { it.businessId != null }} saved by you", onBack)
        ChipRow(Modifier.padding(bottom = 10.dp)) {
            (listOf("All", "Small thermal", "My templates") + (if (pickFor != null) listOf("Saved labels") else emptyList()) + TemplateLibrary.GROUPS)
                .forEach { g -> Chip(g, g == filter, { filter = g }) }
        }
        if (shown.isEmpty()) EmptyState("Nothing here yet",
            if (filter == "My templates") "Open any template, change it, and choose Save as template." else "Templates are still loading.")
        LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown, key = { it.id }) { d ->
                DesignCard(d, onClick = {
                    if (pickFor != null) scope.launch { c.repo.setProductDesign(business.id, pickFor, d.id); onBack() }
                    else onNavigate(Routes.designer(templateId = d.id))
                })
            }
        }
    }
}

/** The Design tab: labels saved by this business. */
@Composable
fun SavedLabelsScreen(onNavigate: (String) -> Unit) {
    val c = LocalContainer.current
    val business = LocalBusiness.current
    val scope = rememberCoroutineScope()
    val saved by remember(business.id) { c.repo.savedDesigns(business.id) }.collectAsStateWithLifecycle(emptyList())
    var deleting by remember { mutableStateOf<LabelDesign?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Saved Labels", "${business.name} · ${saved.size} designs")
        Box(Modifier.padding(horizontal = 16.dp)) {
            PrimaryButton("Create new label", { onNavigate(Routes.templates()) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add)
        }
        if (saved.isEmpty()) EmptyState("No saved labels yet", "Pick a template, change anything you like, then save it. It will appear here.")
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(saved, key = { it.id }) { d ->
                DesignCard(d, onClick = { onNavigate(Routes.designer(designId = d.id)) }) {
                    IconButton(onClick = { scope.launch { c.repo.saveDesign(d.copy(id = 0, name = d.name + " copy"), business.id) } }) {
                        Icon(Icons.Outlined.ContentCopy, "Duplicate design")
                    }
                    IconButton(onClick = { deleting = d }) { Icon(Icons.Outlined.Delete, "Delete design", tint = Fls.Danger) }
                }
            }
        }
    }
    deleting?.let { d ->
        ConfirmDialog("Delete ${d.name}?", "Products that use this design go back to the default layout.", "Delete",
            { scope.launch { c.repo.deleteDesign(d); deleting = null } }, { deleting = null }, danger = true)
    }
}
