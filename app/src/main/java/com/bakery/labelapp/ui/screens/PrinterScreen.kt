package com.bakery.labelapp.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bakery.labelapp.print.PrinterLanguage
import com.bakery.labelapp.print.PrinterType
import com.bakery.labelapp.print.hasBluetoothPermission
import com.bakery.labelapp.print.pairedDevices
import com.bakery.labelapp.print.usbDevices
import com.bakery.labelapp.ui.*

@Composable
fun PrinterScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val saved by vm.printerStore.config.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var cfg by remember(saved) { mutableStateOf(saved) }
    var btGranted by remember { mutableStateOf(hasBluetoothPermission(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        btGranted = hasBluetoothPermission(context)
    }

    Scaffold(
        containerColor = Paper,
        contentWindowInsets = WindowInsets(0),
        topBar = { ScreenHeader("Setup", "Printer", onBack) },
        bottomBar = {
            Column(Modifier.background(Color.White)) {
                HorizontalDivider(color = Line)
                Column(
                    Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { vm.testConnection(cfg) },
                            enabled = !busy && cfg.type != PrinterType.SYSTEM && cfg.isConfigured,
                            modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp),
                        ) { Text("Test connection") }
                        OutlinedButton(
                            onClick = { vm.testPrint(activity, cfg) },
                            enabled = !busy && cfg.isConfigured,
                            modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp),
                        ) { Text("Test print") }
                    }
                    Button(
                        onClick = { vm.savePrinter(cfg) }, enabled = cfg != saved,
                        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                    ) { Text("Save printer", fontWeight = FontWeight.Bold) }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Connection")
            PrinterType.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t ->
                        ChoiceBox(cfg.type == t, t.label, null, Modifier.weight(1f)) {
                            cfg = if (t == cfg.type) cfg else cfg.copy(type = t, address = "", name = "")
                        }
                    }
                }
            }

            when (cfg.type) {
                PrinterType.BLUETOOTH -> {
                    SectionTitle("Paired printers")
                    if (!btGranted) {
                        CardBox {
                            Text("Allow Bluetooth so the app can see your paired printers.")
                            Button(onClick = {
                                if (Build.VERSION.SDK_INT >= 31) permission.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                            }) { Text("Allow Bluetooth") }
                        }
                    } else {
                        val devices = remember(refresh, btGranted) { pairedDevices(context) }
                        if (devices.isEmpty()) {
                            Hint("No paired devices. Turn the printer on, pair it in phone Settings → Bluetooth (PIN is usually 0000 or 1234), then tap Refresh.")
                        }
                        devices.forEach { (name, address) ->
                            ChoiceBox(cfg.address == address, name, address, Modifier.fillMaxWidth()) {
                                cfg = cfg.copy(address = address, name = name)
                            }
                        }
                        TextButton(onClick = { refresh++ }) { Text("Refresh", color = Accent) }
                    }
                }
                PrinterType.WIFI -> {
                    SectionTitle("Network printer")
                    CardBox {
                        OutlinedTextField(
                            value = cfg.address,
                            onValueChange = { cfg = cfg.copy(address = it.trim(), name = it.trim()) },
                            label = { Text("Printer IP address") }, placeholder = { Text("192.168.1.100") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        OutlinedTextField(
                            value = cfg.port.toString(),
                            onValueChange = { t -> cfg = cfg.copy(port = t.filter(Char::isDigit).take(5).toIntOrNull() ?: 0) },
                            label = { Text("Port") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Hint("Phone and printer must be on the same Wi-Fi. Most network label printers use port 9100.")
                    }
                }
                PrinterType.USB -> {
                    SectionTitle("USB devices")
                    val devices = remember(refresh) { usbDevices(context) }
                    if (devices.isEmpty()) Hint("No USB device found. Connect the printer with a USB OTG cable, then tap Refresh.")
                    devices.forEach { (name, id) ->
                        ChoiceBox(cfg.address == id, name, id, Modifier.fillMaxWidth()) { cfg = cfg.copy(address = id, name = name) }
                    }
                    TextButton(onClick = { refresh++ }) { Text("Refresh", color = Accent) }
                }
                PrinterType.SYSTEM -> {
                    CardBox {
                        Text("Uses Android's own print screen. Works with any printer that has a print service app installed, and can also save as PDF. Colours print exactly as in the preview.")
                    }
                }
            }

            if (cfg.type != PrinterType.SYSTEM) {
                SectionTitle("Printer language")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceBox(cfg.language == PrinterLanguage.TSPL, "TSPL", "Label printers (Xprinter, TSC, Gprinter)", Modifier.weight(1f)) {
                        cfg = cfg.copy(language = PrinterLanguage.TSPL)
                    }
                    ChoiceBox(cfg.language == PrinterLanguage.ESCPOS, "ESC/POS", "Receipt-style printers", Modifier.weight(1f)) {
                        cfg = cfg.copy(language = PrinterLanguage.ESCPOS)
                    }
                }
                SectionTitle("Resolution")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(203, 300).forEach { dpi ->
                        ChoiceBox(cfg.dpi == dpi, "$dpi dpi", if (dpi == 203) "Most printers" else "High resolution", Modifier.weight(1f)) {
                            cfg = cfg.copy(dpi = dpi)
                        }
                    }
                }
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Invert image")
                            Hint("Turn on only if labels come out as a negative (white background).")
                        }
                        Switch(checked = cfg.invert, onCheckedChange = { cfg = cfg.copy(invert = it) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
