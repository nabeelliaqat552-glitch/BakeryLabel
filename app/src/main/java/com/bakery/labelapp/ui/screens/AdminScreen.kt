package com.bakery.labelapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bakery.labelapp.label.Dates
import com.bakery.labelapp.ui.*

@Composable
fun AdminScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onProducts: () -> Unit,
    onBatches: () -> Unit,
    onDesign: () -> Unit,
    onPrinter: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Paper,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            ScreenHeader("Admin", "Dashboard", onBack) {
                if (vm.adminUnlocked) IconBox(Icons.Filled.Lock, "Lock admin") { vm.adminUnlocked = false }
            }
        },
    ) { pad ->
        if (!vm.adminUnlocked) {
            PinGate(settings.adminPin, Modifier.padding(pad)) { vm.adminUnlocked = true }
        } else {
            Dashboard(vm, Modifier.padding(pad), onProducts, onBatches, onDesign, onPrinter)
        }
    }
}

@Composable
private fun PinGate(pin: String, modifier: Modifier, onUnlock: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Filled.Lock, contentDescription = null, tint = Ink, modifier = Modifier.size(40.dp))
        Text("Enter admin PIN", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Default PIN is 1234. Change it after your first login.", color = Muted, textAlign = TextAlign.Center)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(8); error = false },
            label = { Text("PIN") },
            singleLine = true,
            isError = error,
            supportingText = { if (error) Text("Wrong PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        Button(
            onClick = { if (text == pin) onUnlock() else error = true },
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
        ) { Text("Unlock", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Dashboard(
    vm: AppViewModel,
    modifier: Modifier,
    onProducts: () -> Unit,
    onBatches: () -> Unit,
    onDesign: () -> Unit,
    onPrinter: () -> Unit,
) {
    val products by vm.allProducts.collectAsStateWithLifecycle()
    val batches by vm.batches.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cloudStatus by vm.cloud.status.collectAsStateWithLifecycle()
    val code by vm.cloud.bakeryCode.collectAsStateWithLifecycle()
    val today = Dates.today()
    val todayBatches = batches.count { it.mfgEpochDay == today }
    val expiringToday = batches.count { it.expEpochDay == today }
    val labelsToday = batches.filter { it.mfgEpochDay == today }.sumOf { it.copiesPrinted }

    var codeText by remember(code) { mutableStateOf(code) }
    var newPin by remember { mutableStateOf("") }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Today")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Batches", "$todayBatches", Modifier.weight(1f))
            Stat("Labels", "$labelsToday", Modifier.weight(1f))
            Stat("Expiring", "$expiringToday", Modifier.weight(1f), if (expiringToday > 0) Danger else Ink)
        }

        SectionTitle("Manage")
        Tile(Icons.Filled.Inventory2, "Products", "${products.size} products · names & shelf life", onProducts)
        Tile(Icons.Filled.History, "Batches", "Dates, status and reprint", onBatches)
        Tile(Icons.Filled.Palette, "Label design", "${sizeText(settings)} · name, colours, fonts", onDesign)
        Tile(Icons.Filled.Print, "Printer", "Bluetooth, Wi-Fi, USB, Android print", onPrinter)

        SectionTitle("Cloud sync")
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Cloud, contentDescription = null, tint = Muted)
                Text(cloudStatus, fontSize = 13.sp, color = LabelInk, modifier = Modifier.weight(1f))
            }
            if (vm.cloud.available) {
                OutlinedTextField(
                    value = codeText, onValueChange = { codeText = it.take(40) },
                    label = { Text("Bakery code") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Use the same code on every phone of this bakery to share products, batches and design.") },
                )
                Button(onClick = { vm.cloud.setBakeryCode(codeText) }, enabled = codeText.isNotBlank() && codeText != code) {
                    Text("Connect")
                }
            } else {
                Hint("Add your Firebase google-services.json and rebuild to turn on cloud sync. Everything works offline meanwhile.")
            }
        }

        SectionTitle("Security")
        CardBox {
            OutlinedTextField(
                value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(8) },
                label = { Text("New admin PIN (4–8 digits)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            Button(onClick = { vm.changePin(newPin); newPin = "" }, enabled = newPin.length >= 4) { Text("Change PIN") }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, color: Color = Ink) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clip(shape).background(Color.White).border(1.dp, Line, shape).padding(12.dp)) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun Tile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Line, shape)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Paper), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, fontSize = 12.sp, color = Muted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Muted)
    }
}
