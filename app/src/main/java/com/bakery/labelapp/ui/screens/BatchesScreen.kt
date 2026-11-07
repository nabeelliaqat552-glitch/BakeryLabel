package com.bakery.labelapp.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bakery.labelapp.data.Batch
import com.bakery.labelapp.data.LabelSettings
import com.bakery.labelapp.label.Dates
import com.bakery.labelapp.ui.*

@Composable
fun BatchesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val batches by vm.batches.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    var reprintOf by remember { mutableStateOf<Batch?>(null) }
    var deleteOf by remember { mutableStateOf<Batch?>(null) }
    val today = Dates.today()

    Scaffold(
        containerColor = Paper,
        contentWindowInsets = WindowInsets(0),
        topBar = { ScreenHeader("History", "Batches", onBack) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Hint("Batch dates are fixed when the batch is printed. They never change when the day changes.") }
            if (batches.isEmpty()) item { Hint("No batches yet. Print a label to create the first one.") }
            items(batches, key = { it.id }) { b ->
                BatchCard(b, settings, today, busy, onReprint = { reprintOf = b }, onDelete = { deleteOf = b })
            }
        }
    }

    reprintOf?.let { b ->
        var n by remember(b.id) { mutableIntStateOf(settings.defaultCopies) }
        AlertDialog(
            onDismissRequest = { reprintOf = null },
            title = { Text("Reprint ${b.productName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("MFG ${Dates.format(b.mfgEpochDay, settings.dateFormat)} · EXP ${Dates.format(b.expEpochDay, settings.dateFormat)}")
                    Stepper(n, { n = it })
                }
            },
            confirmButton = {
                Button(onClick = { vm.reprint(activity, b, n); reprintOf = null }) { Text("Print $n") }
            },
            dismissButton = { TextButton(onClick = { reprintOf = null }) { Text("Cancel") } },
        )
    }

    deleteOf?.let { b ->
        AlertDialog(
            onDismissRequest = { deleteOf = null },
            title = { Text("Remove batch ${b.code}?") },
            text = { Text("It will be hidden from the list on all devices.") },
            confirmButton = {
                Button(onClick = { vm.deleteBatch(b); deleteOf = null }, colors = ButtonDefaults.buttonColors(containerColor = Danger)) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deleteOf = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BatchCard(b: Batch, s: LabelSettings, today: Long, busy: Boolean, onReprint: () -> Unit, onDelete: () -> Unit) {
    val left = (b.expEpochDay - today).toInt()
    val (status, bg, fg) = when {
        left < 0 -> Triple("Expired", BadBg, Danger)
        left == 0 -> Triple("Expires today", WarnBg, WarnFg)
        else -> Triple("Fresh · ${Dates.days(left)} left", GoodBg, Good)
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Line, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Pill(status, bg, fg)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(b.code, fontSize = 12.sp, color = LabelInk)
            Text("MFG ${Dates.format(b.mfgEpochDay, s.dateFormat)}", fontSize = 12.sp, color = LabelInk)
            Text("EXP ${Dates.format(b.expEpochDay, s.dateFormat)}", fontSize = 12.sp, color = Danger, fontWeight = FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${b.copiesPrinted} printed" + if (b.expManual) " · manual expiry" else "",
                fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDelete) { Text("Remove", color = Muted) }
            OutlinedButton(onClick = onReprint, enabled = !busy, shape = RoundedCornerShape(10.dp)) { Text("Reprint") }
        }
    }
}
