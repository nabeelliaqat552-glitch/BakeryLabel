package com.foodlabel.studio.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.foodlabel.studio.ui.theme.Fls
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Large, touch-friendly calendar. Dates are handled as plain calendar days (no time zone shifts). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("Use this date", fontWeight = FontWeight.Bold, color = Fls.Chocolate) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Fls.Muted) } },
        colors = DatePickerDefaults.colors(containerColor = Fls.Card),
    ) {
        DatePicker(state = state, colors = DatePickerDefaults.colors(
            containerColor = Fls.Card, selectedDayContainerColor = Fls.Chocolate, todayDateBorderColor = Fls.GoldRing,
            todayContentColor = Fls.Ink,
        ))
    }
}
