package com.bakery.labelapp.label

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {
    val FORMATS = listOf("dd-MM-yyyy", "dd/MM/yyyy", "dd.MM.yyyy", "dd MMM yyyy", "yyyy-MM-dd")

    fun today(): Long = LocalDate.now().toEpochDay()

    fun format(epochDay: Long, pattern: String): String = try {
        LocalDate.ofEpochDay(epochDay)
            .format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
            .uppercase(Locale.ENGLISH)
    } catch (e: IllegalArgumentException) {
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
    }

    fun days(n: Int): String = if (n == 1) "1 day" else "$n days"
}

/** 50.0 -> "50", 2.5 -> "2.5" */
fun mm(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
