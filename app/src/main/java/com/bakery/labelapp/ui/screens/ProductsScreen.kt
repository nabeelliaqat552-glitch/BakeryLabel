package com.bakery.labelapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bakery.labelapp.data.Product
import com.bakery.labelapp.label.Dates
import com.bakery.labelapp.ui.*

@Composable
fun ProductsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val products by vm.allProducts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Product?>(null) }

    Scaffold(
        containerColor = Paper,
        contentWindowInsets = WindowInsets(0),
        topBar = { ScreenHeader("Admin", "Products", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Product(name = "", shelfLifeDays = 1) },
                containerColor = Ink, contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add product") },
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Hint("Shelf life is used for new batches only. Batches already printed keep their dates.") }
            items(products, key = { it.id }) { p ->
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Line, shape)
                        .clickable { editing = p }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (p.active) Ink else Muted)
                        if (!p.active) Hint("Hidden from print screen")
                    }
                    Pill(Dates.days(p.shelfLifeDays), Paper, Ink)
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${p.name}", tint = Muted)
                }
            }
        }
    }

    editing?.let { p ->
        ProductDialog(
            product = p,
            isNew = products.none { it.id == p.id },
            onDismiss = { editing = null },
            onSave = { vm.saveProduct(it); editing = null },
        )
    }
}

@Composable
private fun ProductDialog(product: Product, isNew: Boolean, onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var name by remember(product.id) { mutableStateOf(product.name) }
    var days by remember(product.id) { mutableStateOf(product.shelfLifeDays.toString()) }
    var active by remember(product.id) { mutableStateOf(product.active) }
    val d = days.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add product" else "Edit product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) },
                    label = { Text("Product name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                OutlinedTextField(
                    value = days, onValueChange = { days = it.filter(Char::isDigit).take(4) },
                    label = { Text("Shelf life (days)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Expiry = manufacturing date + this many days") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show on print screen", modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && d != null,
                onClick = { onSave(product.copy(name = name, shelfLifeDays = d ?: 0, active = active)) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
