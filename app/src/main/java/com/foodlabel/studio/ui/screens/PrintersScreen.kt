package com.foodlabel.studio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodlabel.studio.data.db.PrinterEntity
import com.foodlabel.studio.printing.Output
import com.foodlabel.studio.printing.PreparedLabel
import com.foodlabel.studio.printing.connection.ConnectionType
import com.foodlabel.studio.printing.connection.Discovery
import com.foodlabel.studio.printing.connection.FoundPrinter
import com.foodlabel.studio.printing.driver.PrinterProtocol
import com.foodlabel.studio.ui.LocalBusiness
import com.foodlabel.studio.ui.LocalContainer
import com.foodlabel.studio.ui.Routes
import com.foodlabel.studio.ui.components.CardBlock
import com.foodlabel.studio.ui.components.Chip
import com.foodlabel.studio.ui.components.ConfirmDialog
import com.foodlabel.studio.ui.components.Field
import com.foodlabel.studio.ui.components.Notice
import com.foodlabel.studio.ui.components.NoticeKind
import com.foodlabel.studio.ui.components.NumberField
import com.foodlabel.studio.ui.components.PrimaryButton
import com.foodlabel.studio.ui.components.ScreenHeader
import com.foodlabel.studio.ui.components.SecondaryButton
import com.foodlabel.studio.ui.components.SectionLabel
import com.foodlabel.studio.ui.components.Segmented
import com.foodlabel.studio.ui.components.Stepper
import com.foodlabel.studio.ui.components.ToggleRow
import com.foodlabel.studio.ui.theme.Fls
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun ConnTile(icon: ImageVector, title: String, sub: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = if (selected) Fls.Chocolate else Fls.Card,
        border = BorderStroke(1.dp, if (selected) Fls.Chocolate else Fls.Line), modifier = modifier.heightIn(min = 96.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = if (selected) Fls.Gold else Fls.GoldDeep)
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (selected) Fls.Ivory else Fls.Ink)
            Text(sub, fontSize = 11.sp, color = if (selected) Fls.Sand else Fls.Muted)
        }
    }
}

@Composable
private fun DeviceRow(p: FoundPrinter, action: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Fls.Card) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (p.paired) Fls.Success else Fls.Line))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${p.detail} · ${p.address}", style = MaterialTheme.typography.bodySmall)
            }
            Text(action, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Fls.Chocolate)
        }
    }
}

@Composable
fun PrintersScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val c = LocalContainer.current
    val business = LocalBusiness.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val printers by remember(business.id) { c.repo.printers(business.id) }.collectAsStateWithLifecycle(emptyList())

    var type by rememberSaveable { mutableStateOf(ConnectionType.BLUETOOTH) }
    val found = remember { mutableStateListOf<FoundPrinter>() }
    var scanning by remember { mutableStateOf(false) }
    var scanTick by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    var ip by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("9100") }

    fun create(p: FoundPrinter) = scope.launch {
        val defaults = when (p.type) {
            ConnectionType.ANDROID_PRINT -> PrinterProtocol.ANDROID_PRINT
            else -> PrinterProtocol.TSPL
        }
        val id = c.repo.savePrinter(PrinterEntity(businessId = business.id, name = p.name, connection = p.type.name, address = p.address,
            port = port.toIntOrNull() ?: 9100, protocol = defaults.name, headWidthMm = if (defaults == PrinterProtocol.TSPL) 104.0 else 210.0))
        onNavigate(Routes.printer(id))
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) scanTick++ else note = "Bluetooth permission was not given, so the app cannot look for printers. You can allow it in the phone's Settings › Apps › FoodLabel Studio."
    }

    // Bluetooth scan, restarted by scanTick.
    LaunchedEffect(type, scanTick) {
        found.clear(); note = null
        when (type) {
            ConnectionType.BLUETOOTH, ConnectionType.BLE -> {
                if (!Discovery.hasScanPermission(context)) { note = "Tap Search to allow Bluetooth and look for printers."; return@LaunchedEffect }
                if (!Discovery.isBluetoothOn(context)) { note = "Bluetooth is off. Turn it on, then tap Search."; return@LaunchedEffect }
                found.addAll(Discovery.pairedDevices(context))
                scanning = true
                try {
                    kotlinx.coroutines.withTimeoutOrNull(15_000) {
                        Discovery.scanBluetooth(context).collect { d ->
                            val i = found.indexOfFirst { it.address == d.address }
                            if (i < 0) found.add(d) else if (!found[i].paired) found[i] = d
                        }
                    }
                } finally { scanning = false }
            }
            ConnectionType.USB -> {
                while (true) {
                    val list = Discovery.usbDevices(context).map { it.first }
                    found.clear(); found.addAll(list)
                    if (list.isEmpty()) note = "No USB device found. Connect the printer with an OTG adapter and switch it on. Not every phone supports USB OTG."
                    else note = null
                    delay(1500)
                }
            }
            ConnectionType.NETWORK -> if (scanTick > 0) {
                scanning = true
                try { Discovery.scanNetwork(port.toIntOrNull() ?: 9100) { found.add(it) } } finally { scanning = false }
                if (found.isEmpty()) note = "No printer answered on this Wi-Fi. Enter the printer's IP address by hand (print the printer's self-test page to see it)."
            }
            ConnectionType.ANDROID_PRINT -> {}
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Printer Manager", business.name, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (printers.isNotEmpty()) CardBlock {
                SectionLabel("Your printers")
                printers.forEach { p ->
                    Surface(onClick = { onNavigate(Routes.printer(p.id)) }, color = Fls.Card) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name + if (p.isDefault) " · Default" else "", fontWeight = FontWeight.Bold)
                                Text("${ConnectionType.parse(p.connection).label} · ${PrinterProtocol.parse(p.protocol).label} · ${p.dpi} DPI" +
                                    if (p.verified) " · test confirmed" else " · not tested yet", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Open", fontWeight = FontWeight.Bold, color = Fls.Chocolate)
                        }
                    }
                }
            }
            SectionLabel("Add a printer · Step 1: how is it connected?")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ConnTile(Icons.Outlined.Bluetooth, "Bluetooth", "Classic & BLE", type == ConnectionType.BLUETOOTH, Modifier.weight(1f)) { type = ConnectionType.BLUETOOTH }
                ConnTile(Icons.Outlined.Usb, "USB cable", "OTG adapter", type == ConnectionType.USB, Modifier.weight(1f)) { type = ConnectionType.USB }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ConnTile(Icons.Outlined.Wifi, "Wi-Fi / LAN", "IP address", type == ConnectionType.NETWORK, Modifier.weight(1f)) { type = ConnectionType.NETWORK }
                ConnTile(Icons.Outlined.Print, "Android Print", "Print service / PDF", type == ConnectionType.ANDROID_PRINT, Modifier.weight(1f)) { type = ConnectionType.ANDROID_PRINT }
            }

            CardBlock {
                when (type) {
                    ConnectionType.BLUETOOTH, ConnectionType.BLE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("Step 2: choose your printer", Modifier.weight(1f))
                            TextButton(onClick = {
                                if (Discovery.hasScanPermission(context)) scanTick++ else permission.launch(Discovery.bluetoothPermissions())
                            }) { Text(if (scanning) "Searching…" else "Search") }
                        }
                        found.forEach { p ->
                            HorizontalDivider(color = Fls.LineSoft)
                            DeviceRow(p, if (p.paired || p.type == ConnectionType.BLE) "Add" else "Pair") {
                                if (p.paired || p.type == ConnectionType.BLE) create(p)
                                else { Discovery.pair(context, p.address); note = "Accept the pairing request on the phone (the PIN is often 0000 or 1234), then tap Search again." }
                            }
                        }
                    }
                    ConnectionType.USB -> {
                        SectionLabel("Step 2: choose the USB device")
                        found.forEach { p ->
                            HorizontalDivider(color = Fls.LineSoft)
                            DeviceRow(p, if (p.paired) "Add" else "Allow") {
                                if (p.paired) create(p)
                                else Discovery.usbDevices(context).firstOrNull { it.first.address == p.address }?.let { Discovery.requestUsbPermission(context, it.second) }
                            }
                        }
                    }
                    ConnectionType.NETWORK -> {
                        SectionLabel("Step 2: enter the printer's address")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Field(ip, { ip = it.trim() }, "IP address, e.g. 192.168.1.40", Modifier.weight(2f), keyboard = KeyboardType.Decimal)
                            Field(port, { port = it.filter(Char::isDigit) }, "Port", Modifier.weight(1f), keyboard = KeyboardType.Number)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(if (scanning) "Searching…" else "Find on Wi-Fi", { scanTick++ }, modifier = Modifier.weight(1f), enabled = !scanning)
                            PrimaryButton("Add", { create(FoundPrinter("Network printer $ip", ip, ConnectionType.NETWORK)) }, modifier = Modifier.weight(1f),
                                enabled = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(ip), height = 52.dp)
                        }
                        found.forEach { p -> HorizontalDivider(color = Fls.LineSoft); DeviceRow(p, "Add") { create(p) } }
                    }
                    ConnectionType.ANDROID_PRINT -> {
                        SectionLabel("Step 2: use Android's print system")
                        Text("Labels are sent as a PDF with pages exactly the label size to any print service installed on this phone " +
                            "(for example the printer maker's plugin). You can also save the PDF.", style = MaterialTheme.typography.bodyMedium)
                        PrimaryButton("Add Android Print", { create(FoundPrinter("Android Print", "", ConnectionType.ANDROID_PRINT)) }, Modifier.fillMaxWidth())
                    }
                }
                note?.let { Notice(it, NoticeKind.Info) }
            }
            Text("Step 3 is on the next screen: choose the printer language, print a test label and confirm it came out right. " +
                "The app does not claim a printer works until you confirm a test.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PrinterEditScreen(printerId: Long, onBack: () -> Unit) {
    val c = LocalContainer.current
    val business = LocalBusiness.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles by remember { c.repo.printerProfiles() }.collectAsStateWithLifecycle(emptyList())
    var p by remember { mutableStateOf<PrinterEntity?>(null) }
    var status by remember { mutableStateOf<Pair<String, NoticeKind>?>(null) }
    var testing by remember { mutableStateOf(false) }
    var askConfirm by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(printerId, business.id) { p = c.repo.getPrinter(business.id, printerId) }
    val pr = p
    if (pr == null) { Column { ScreenHeader("Printer", null, onBack) }; return }
    fun set(n: PrinterEntity) { p = n; scope.launch { c.repo.savePrinter(n) } }
    val protocol = PrinterProtocol.parse(pr.protocol)
    val type = ConnectionType.parse(pr.connection)
    val raw = type != ConnectionType.ANDROID_PRINT

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(pr.name, "${type.label}${if (pr.address.isNotBlank()) " · ${pr.address}" else ""}", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CardBlock {
                Field(pr.name, { set(pr.copy(name = it)) }, "Printer name")
                if (type == ConnectionType.NETWORK) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(pr.address, { set(pr.copy(address = it.trim())) }, "IP address", Modifier.weight(2f))
                    Field(pr.port.toString(), { v -> v.toIntOrNull()?.let { set(pr.copy(port = it)) } }, "Port", Modifier.weight(1f), keyboard = KeyboardType.Number)
                }
                ToggleRow("Default printer for ${business.name}", pr.isDefault, { on -> if (on) { p = pr.copy(isDefault = true); scope.launch { c.repo.makeDefaultPrinter(business.id, pr.id) } } })
            }
            if (raw) {
                CardBlock {
                    SectionLabel("Printer language")
                    Text("Printers cannot reliably tell the app which language they speak, so choose it here. It is written in the printer's manual. " +
                        "Label printers are usually TSPL; receipt printers are usually ESC/POS.", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrinterProtocol.entries.filter { it.isRaw }.forEach { pp -> Chip(pp.label, pp == protocol, {
                            // Receipt-style heads are usually 48 mm (58 mm paper); label printers are usually wider.
                            val head = if (pp == PrinterProtocol.ESC_POS) 48.0 else if (pr.headWidthMm <= 48.0) 104.0 else pr.headWidthMm
                            set(pr.copy(protocol = pp.name, headWidthMm = head, profileId = null, verified = false))
                        }) }
                    }
                    Notice(protocol.description, NoticeKind.Info)
                    SectionLabel("Or start from a profile")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        profiles.forEach { pf -> Chip(pf.name, pr.profileId == pf.id, {
                            set(pr.copy(profileId = pf.id, protocol = pf.protocol, dpi = pf.dpi, headWidthMm = pf.headWidthMm, verified = false))
                        }) }
                    }
                }
                CardBlock {
                    SectionLabel("Print settings")
                    Text("Resolution", fontWeight = FontWeight.Bold)
                    Segmented(listOf(203, 300, 600), pr.dpi, { "$it DPI" }, { set(pr.copy(dpi = it, verified = false)) }, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(pr.headWidthMm, { set(pr.copy(headWidthMm = it.coerceIn(10.0, 300.0))) }, "Print width (mm)", Modifier.weight(1f), resetKey = "${pr.protocol}/${pr.profileId}")
                        NumberField(pr.offsetXmm, { set(pr.copy(offsetXmm = it.coerceIn(0.0, 50.0))) }, "Shift right (mm)", Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Darkness", Modifier.weight(1f), fontWeight = FontWeight.Bold); Stepper(pr.density, { set(pr.copy(density = it)) }, 0, 15) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Speed", Modifier.weight(1f), fontWeight = FontWeight.Bold); Stepper(pr.speed, { set(pr.copy(speed = it)) }, 1, 6) }
                    Text("Labels always print at their real size. If a label is wider than the print width, the app stops and tells you instead of shrinking it.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            CardBlock {
                SectionLabel("Test & calibration")
                Text("Prints one label with a border, 5 mm marks, Urdu text and a barcode. Measure it with a ruler and scan the barcode.", style = MaterialTheme.typography.bodyMedium)
                status?.let { Notice(it.first, it.second) }
                if (askConfirm) {
                    Text("Did the test label print correctly?", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("No", {
                            askConfirm = false; set(pr.copy(verified = false))
                            status = "Try another printer language or resolution, then test again. If nothing prints at all, check paper, cover and battery." to NoticeKind.Warning
                        }, modifier = Modifier.weight(1f))
                        PrimaryButton("Yes", { askConfirm = false; set(pr.copy(verified = true)); status = "Saved as a confirmed working setup." to NoticeKind.Success },
                            modifier = Modifier.weight(1f), height = 52.dp)
                    }
                }
                PrimaryButton(if (testing) "Sending…" else "Print test label", {
                    testing = true; status = null; askConfirm = false
                    scope.launch {
                        val size = business.defaultLabelSizeId?.let { c.repo.getLabelSize(it) }
                        val w = size?.widthMm ?: 50.0; val h = size?.heightMm ?: 30.0
                        try {
                            if (raw) {
                                Output.sendTestLabel(context, pr, w, h)
                                status = "Test label sent to the printer. This only means the printer accepted the data." to NoticeKind.Info
                                askConfirm = true
                            } else {
                                val label = PreparedLabel(Output.calibrationDesign(w, h, 300), Output.calibrationData, 1,
                                    com.foodlabel.studio.data.db.PrintHistoryEntity(jobId = 0, businessId = business.id, productId = null, productName = "Test label",
                                        copies = 1, mfgDate = "", expDate = "", batchNumber = "", designName = "Test"))
                                Output.printWithAndroid(context, "FoodLabel test", listOf(label)) { ok, err ->
                                    status = if (ok) "Test page handed to Android Print." to NoticeKind.Info else (err ?: "Android Print failed.") to NoticeKind.Error
                                    askConfirm = ok
                                }
                            }
                        } catch (e: Exception) {
                            status = (e.message ?: "Could not print.") to NoticeKind.Error
                        } finally { testing = false }
                    }
                }, enabled = !testing, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.Print)
            }
            SecondaryButton("Remove this printer", { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), danger = true, icon = Icons.Outlined.Delete)
        }
        Box(Modifier.padding(16.dp).navigationBarsPadding()) { PrimaryButton("Done", onBack, Modifier.fillMaxWidth()) }
    }
    if (confirmDelete) ConfirmDialog("Remove ${pr.name}?", "Print history is kept.", "Remove",
        { confirmDelete = false; scope.launch { c.repo.deletePrinter(pr); onBack() } }, { confirmDelete = false }, danger = true)
}
