package com.foodlabel.studio.dates

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How a product's shelf life is counted. The business user always sets the number. */
enum class ShelfLifeUnit(val label: String) {
    DAYS("Days"), WEEKS("Weeks"), MONTHS("Months");

    companion object {
        fun parse(value: String?): ShelfLifeUnit =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DAYS
    }
}

/** Date formats a business can choose for its labels. */
enum class DateFormatOption(val pattern: String, val label: String) {
    DMY_DASH("dd-MM-yyyy", "DD-MM-YYYY"),
    DMY_SLASH("dd/MM/yyyy", "DD/MM/YYYY"),
    ISO("yyyy-MM-dd", "YYYY-MM-DD"),
    D_MON_Y("dd MMM yyyy", "DD MMM YYYY");

    companion object {
        fun parse(value: String?): DateFormatOption =
            entries.firstOrNull { it.name == value } ?: DMY_DASH
    }
}

/**
 * Result of resolving the dates for one label.
 * [expiryIsManual] is true when the user picked the expiry date themselves; such a date is
 * never recalculated.
 */
data class ResolvedDates(
    val manufactured: LocalDate,
    val manufacturedTime: LocalTime?,
    val expiry: LocalDate?,
    val expiryIsManual: Boolean,
    val warning: String?,
)

object DateEngine {

    /**
     * Calendar-correct expiry calculation.
     * - Days and weeks are added on the calendar, so month ends and leap days are handled.
     * - Months use [LocalDate.plusMonths], which clamps to the last valid day
     *   (31 Jan + 1 month = 28 Feb, or 29 Feb in a leap year).
     */
    fun calculateExpiry(manufactured: LocalDate, value: Int, unit: ShelfLifeUnit): LocalDate {
        require(value >= 0) { "Shelf life cannot be negative" }
        return when (unit) {
            ShelfLifeUnit.DAYS -> manufactured.plusDays(value.toLong())
            ShelfLifeUnit.WEEKS -> manufactured.plusWeeks(value.toLong())
            ShelfLifeUnit.MONTHS -> manufactured.plusMonths(value.toLong())
        }
    }

    /**
     * Decide the dates for a label.
     * @param manualExpiry when not null, it is used exactly as given.
     * @param shelfLifeValue null when the product has no shelf life set; then no expiry is invented.
     */
    fun resolve(
        manufactured: LocalDate,
        manufacturedTime: LocalTime? = null,
        shelfLifeValue: Int?,
        shelfLifeUnit: ShelfLifeUnit,
        manualExpiry: LocalDate? = null,
    ): ResolvedDates {
        if (manualExpiry != null) {
            val warning = if (manualExpiry.isBefore(manufactured))
                "Expiry date is before the manufacturing date." else null
            return ResolvedDates(manufactured, manufacturedTime, manualExpiry, true, warning)
        }
        if (shelfLifeValue == null) {
            return ResolvedDates(
                manufactured, manufacturedTime, null, false,
                "No shelf life set for this product. Set one or pick an expiry date."
            )
        }
        val expiry = calculateExpiry(manufactured, shelfLifeValue, shelfLifeUnit)
        return ResolvedDates(manufactured, manufacturedTime, expiry, false, null)
    }

    fun format(date: LocalDate?, option: DateFormatOption): String =
        date?.format(DateTimeFormatter.ofPattern(option.pattern, Locale.ENGLISH))?.uppercase(Locale.ENGLISH) ?: ""

    fun formatTime(time: LocalTime?): String =
        time?.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)) ?: ""

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    fun describeShelfLife(value: Int?, unit: ShelfLifeUnit): String = when {
        value == null -> "Not set"
        value == 1 -> "1 ${unit.label.lowercase(Locale.ENGLISH).removeSuffix("s")}"
        else -> "$value ${unit.label.lowercase(Locale.ENGLISH)}"
    }
}
