package com.foodlabel.studio.core

import kotlin.math.roundToInt

/** Physical unit conversions. All label geometry is stored in millimetres. */
object Units {
    const val MM_PER_INCH = 25.4

    fun mmToDots(mm: Double, dpi: Int): Int = (mm * dpi / MM_PER_INCH).roundToInt()
    fun dotsToMm(dots: Int, dpi: Int): Double = dots * MM_PER_INCH / dpi
    fun mmToInch(mm: Double): Double = mm / MM_PER_INCH
    fun inchToMm(inch: Double): Double = inch * MM_PER_INCH
    fun mmToPoints(mm: Double): Double = mm * 72.0 / MM_PER_INCH
    fun ptToDots(pt: Double, dpi: Int): Double = pt * dpi / 72.0
    fun mmToMils(mm: Double): Int = (mm / MM_PER_INCH * 1000).roundToInt()

    fun formatMm(mm: Double): String =
        if (mm % 1.0 == 0.0) mm.toInt().toString() else String.format(java.util.Locale.ENGLISH, "%.1f", mm)
}
