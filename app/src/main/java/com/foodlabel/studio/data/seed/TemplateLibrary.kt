package com.foodlabel.studio.data.seed

import com.foodlabel.studio.barcode.BarcodeType
import com.foodlabel.studio.label.ElementType
import com.foodlabel.studio.label.FontOption
import com.foodlabel.studio.label.LabelDesign
import com.foodlabel.studio.label.LabelElement
import com.foodlabel.studio.label.TextAlignOption
import kotlin.math.max
import kotlin.math.min

/**
 * Built-in template library. Every template is an editable layout made of elements, not an image.
 * Layouts are computed from the label size, so a template keeps its proportions on every size.
 */
object TemplateLibrary {

    private enum class Layout { STANDARD, EXPIRY, PRICE, BARCODE, BILINGUAL, MINIMAL, ROUND, INVERTED, MANUFACTURING, COMPACT }

    private data class Spec(
        val group: String, val name: String, val layout: Layout, val w: Double, val h: Double,
        val serif: Boolean = false, val border: Double = 0.0, val inset: Double = 0.0, val corner: Double = 0.0,
    )

    private val specs = listOf(
        Spec("Minimal Bakery", "Minimal Bakery", Layout.STANDARD, 50.0, 30.0, serif = true),
        Spec("Minimal Bakery", "Minimal Bakery Slim", Layout.MINIMAL, 40.0, 30.0, serif = true),
        Spec("Minimal Bakery", "Minimal Bakery Large", Layout.MINIMAL, 60.0, 40.0, serif = true),
        Spec("Luxury Bakery", "Luxury Bakery Frame", Layout.BILINGUAL, 60.0, 40.0, serif = true, border = 0.35, inset = 1.2),
        Spec("Luxury Bakery", "Luxury Bakery Band", Layout.INVERTED, 70.0, 50.0, serif = true),
        Spec("Luxury Bakery", "Luxury Bakery Classic", Layout.BILINGUAL, 50.0, 30.0, serif = true, border = 0.3, inset = 1.0),
        Spec("Modern Food", "Modern Food", Layout.STANDARD, 50.0, 30.0),
        Spec("Modern Food", "Modern Food Header", Layout.INVERTED, 60.0, 40.0),
        Spec("Modern Food", "Modern Food Wide", Layout.STANDARD, 80.0, 50.0),
        Spec("Classic Food", "Classic Food Border", Layout.BILINGUAL, 50.0, 25.0, serif = true, border = 0.4),
        Spec("Classic Food", "Classic Food", Layout.STANDARD, 60.0, 30.0, serif = true, border = 0.3),
        Spec("Premium Sweets", "Premium Sweets Frame", Layout.BILINGUAL, 60.0, 40.0, serif = true, border = 0.4, inset = 1.0, corner = 2.0),
        Spec("Premium Sweets", "Premium Sweets Seal", Layout.ROUND, 50.0, 50.0, serif = true, border = 0.4),
        Spec("Premium Sweets", "Premium Sweets Band", Layout.INVERTED, 50.0, 30.0, serif = true),
        Spec("Dairy Fresh", "Dairy Fresh Expiry", Layout.EXPIRY, 50.0, 30.0),
        Spec("Dairy Fresh", "Dairy Fresh Large", Layout.EXPIRY, 60.0, 40.0),
        Spec("Dairy Fresh", "Dairy Fresh Strip", Layout.STANDARD, 50.0, 25.0),
        Spec("Clean Retail", "Clean Retail Price", Layout.PRICE, 40.0, 30.0),
        Spec("Clean Retail", "Clean Retail Barcode", Layout.BARCODE, 50.0, 30.0),
        Spec("Clean Retail", "Clean Retail Shelf", Layout.STANDARD, 100.0, 50.0),
        Spec("Price Sticker", "Price Sticker Small", Layout.COMPACT, 30.0, 15.0),
        Spec("Price Sticker", "Price Sticker", Layout.PRICE, 40.0, 20.0),
        Spec("Price Sticker", "Price Sticker Mini", Layout.COMPACT, 25.0, 15.0),
        Spec("Barcode Label", "Barcode Label Small", Layout.BARCODE, 40.0, 20.0),
        Spec("Barcode Label", "Barcode Label", Layout.BARCODE, 60.0, 30.0),
        Spec("Barcode Label", "Barcode Label Strip", Layout.BARCODE, 50.0, 25.0),
        Spec("Expiry Label", "Expiry Label Small", Layout.EXPIRY, 40.0, 20.0),
        Spec("Expiry Label", "Expiry Label", Layout.EXPIRY, 40.0, 30.0),
        Spec("Expiry Label", "Expiry Label Strip", Layout.EXPIRY, 50.0, 20.0),
        Spec("Compact Thermal", "Compact Thermal 20×10", Layout.COMPACT, 20.0, 10.0),
        Spec("Compact Thermal", "Compact Thermal 25×15", Layout.COMPACT, 25.0, 15.0),
        Spec("Compact Thermal", "Compact Thermal 30×15", Layout.COMPACT, 30.0, 15.0),
        Spec("Compact Thermal", "Compact Thermal 40×20", Layout.COMPACT, 40.0, 20.0),
        Spec("Small Product Label", "Small Product", Layout.STANDARD, 40.0, 30.0),
        Spec("Small Product Label", "Small Product Bilingual", Layout.BILINGUAL, 40.0, 20.0),
        Spec("Rectangle Label", "Rectangle", Layout.STANDARD, 70.0, 50.0),
        Spec("Rectangle Label", "Rectangle Bilingual", Layout.BILINGUAL, 80.0, 50.0),
        Spec("Square Label", "Square Centered", Layout.BILINGUAL, 40.0, 40.0),
        Spec("Square Label", "Square", Layout.STANDARD, 50.0, 50.0),
        Spec("Round Label", "Round", Layout.ROUND, 40.0, 40.0),
        Spec("Round Label", "Round Small", Layout.ROUND, 30.0, 30.0),
        Spec("Black & White", "Black Band", Layout.INVERTED, 50.0, 30.0),
        Spec("Black & White", "Black Band Small", Layout.INVERTED, 40.0, 30.0),
        Spec("Minimal Typography", "Typography Strip", Layout.MINIMAL, 50.0, 25.0),
        Spec("Minimal Typography", "Typography", Layout.MINIMAL, 50.0, 30.0),
        Spec("Professional Manufacturing", "Manufacturing", Layout.MANUFACTURING, 60.0, 40.0),
        Spec("Professional Manufacturing", "Manufacturing Wide", Layout.MANUFACTURING, 80.0, 50.0),
        Spec("Professional Manufacturing", "Manufacturing Shelf", Layout.MANUFACTURING, 100.0, 50.0),
    )

    val GROUPS: List<String> = specs.map { it.group }.distinct()

    fun all(): List<LabelDesign> = specs.mapIndexed { i, s -> build(s, "builtin_${i + 1}") }

    /** Starting layout used by "Create New Label" for any size. */
    fun blankStarter(w: Double, h: Double): LabelDesign =
        build(Spec("Custom", "New label", if (h < 20) Layout.COMPACT else Layout.STANDARD, w, h), null).copy(isTemplate = false)

    // ---- layout builders ----------------------------------------------------------------------

    private class B(val w: Double, val h: Double, val font: FontOption) {
        val els = mutableListOf<LabelElement>()
        val s = (min(w, h * 1.6) / 50.0).coerceIn(0.45, 1.8)
        val p = max(1.0, min(w, h) * 0.05)
        private var z = 0

        fun text(x: Double, y: Double, ww: Double, hh: Double, content: String, pt: Double, bold: Boolean = false,
                 align: TextAlignOption = TextAlignOption.START, inverted: Boolean = false, f: FontOption = font, lines: Int = 1) {
            els += LabelElement(type = ElementType.TEXT, xMm = x, yMm = y, wMm = ww, hMm = hh, content = content,
                fontPt = pt, bold = bold, align = align, inverted = inverted, font = f, z = z++, maxLines = lines)
        }

        fun barcode(x: Double, y: Double, ww: Double, hh: Double, hr: Boolean = true) {
            els += LabelElement(type = ElementType.BARCODE, xMm = x, yMm = y, wMm = ww, hMm = hh, content = "{barcode}",
                barcodeType = BarcodeType.CODE_128, humanReadable = hr, fontPt = max(5.0, 6.0 * s), z = z++)
        }

        fun qr(x: Double, y: Double, size: Double) {
            els += LabelElement(type = ElementType.QR, xMm = x, yMm = y, wMm = size, hMm = size,
                content = "{name_en}|{batch}|{mfg}|{exp}", z = z++)
        }

        fun line(y: Double, x: Double = p, ww: Double = w - 2 * p, stroke: Double = 0.3) {
            els += LabelElement(type = ElementType.LINE, xMm = x, yMm = y, wMm = ww, hMm = stroke, strokeMm = stroke, z = z++)
        }

        fun box(x: Double, y: Double, ww: Double, hh: Double, filled: Boolean) {
            els += LabelElement(type = ElementType.BOX, xMm = x, yMm = y, wMm = ww, hMm = hh, inverted = filled, strokeMm = 0.3, z = z++)
        }
    }

    private fun build(spec: Spec, key: String?): LabelDesign {
        val font = if (spec.serif) FontOption.SERIF else FontOption.SANS
        val b = B(spec.w, spec.h, font)
        val w = spec.w; val h = spec.h; val p = b.p + spec.inset; val s = b.s
        val iw = w - 2 * p
        val C = TextAlignOption.CENTER; val E = TextAlignOption.END
        when (spec.layout) {
            Layout.STANDARD -> {
                b.text(p, p, iw * 0.56, h * 0.17 - p, "{business}", 6.0 * s, bold = true, f = FontOption.SANS)
                b.text(p, h * 0.17, iw * 0.56, h * 0.22, "{name_en}", 12.0 * s, bold = true)
                b.text(p + iw * 0.58, h * 0.10, iw * 0.42, h * 0.30, "{name_ur}", 11.0 * s, align = E)
                b.line(h * 0.42)
                b.text(p, h * 0.45, iw * 0.5, h * 0.12, "MFG {mfg}", 7.0 * s)
                b.text(p + iw * 0.5, h * 0.45, iw * 0.5, h * 0.12, "EXP {exp}", 7.0 * s, bold = true, align = E)
                b.text(p, h * 0.57, iw * 0.5, h * 0.11, "{price}", 7.0 * s, bold = true)
                b.text(p + iw * 0.5, h * 0.57, iw * 0.5, h * 0.11, "{weight}", 7.0 * s, align = E)
                b.barcode(p + iw * 0.1, h * 0.70, iw * 0.8, h - p - h * 0.70)
            }
            Layout.EXPIRY -> {
                b.text(p, p, iw * 0.6, h * 0.2, "{name_en}", 10.0 * s, bold = true)
                b.text(p + iw * 0.62, p, iw * 0.38, h * 0.24, "{name_ur}", 10.0 * s, align = E)
                b.text(p, h * 0.30, iw, h * 0.30, "EXP {exp}", 13.0 * s, bold = true, align = C, inverted = true, f = FontOption.SANS)
                b.text(p, h * 0.64, iw * 0.5, h * 0.14, "MFG {mfg}", 7.0 * s)
                b.text(p + iw * 0.5, h * 0.64, iw * 0.5, h * 0.14, "{price}", 7.5 * s, bold = true, align = E)
                b.text(p, h * 0.80, iw * 0.5, h - p - h * 0.80, "{weight}", 6.5 * s)
                b.text(p + iw * 0.5, h * 0.80, iw * 0.5, h - p - h * 0.80, "Batch {batch}", 6.5 * s, align = E)
            }
            Layout.PRICE -> {
                b.text(p, p, iw, h * 0.22 - p, "{name_en}", 9.0 * s, bold = true, align = C)
                b.text(p, h * 0.22, iw, h * 0.2, "{name_ur}", 9.0 * s, align = C)
                b.text(p, h * 0.42, iw, h * 0.33, "{price}", 18.0 * s, bold = true, align = C, f = FontOption.SANS)
                b.line(h * 0.78)
                b.text(p, h * 0.80, iw, h - p - h * 0.80, "EXP {exp}", 6.5 * s, align = C)
            }
            Layout.BARCODE -> {
                b.text(p, p, iw * 0.65, h * 0.2, "{name_en}", 8.5 * s, bold = true)
                b.text(p + iw * 0.65, p, iw * 0.35, h * 0.2, "{price}", 8.5 * s, bold = true, align = E)
                b.barcode(p, h * 0.26, iw, h * 0.56)
                b.text(p, h * 0.84, iw, h - p - h * 0.84, "EXP {exp}", 6.0 * s, align = C)
            }
            Layout.BILINGUAL -> {
                b.text(p, p, iw, h * 0.17 - p, "{business}", 6.0 * s, bold = true, align = C, f = FontOption.SANS)
                b.text(p, h * 0.17, iw, h * 0.2, "{name_en}", 12.0 * s, bold = true, align = C)
                b.text(p, h * 0.37, iw, h * 0.22, "{name_ur}", 11.0 * s, align = C)
                b.line(h * 0.61, p + iw * 0.25, iw * 0.5, 0.25)
                b.text(p, h * 0.64, iw, h * 0.14, "MFG {mfg}  ·  EXP {exp}", 7.0 * s, align = C)
                b.text(p, h * 0.79, iw, h - p - h * 0.79, "{price}   {weight}", 7.0 * s, bold = true, align = C)
            }
            Layout.MINIMAL -> {
                b.text(p, p, iw, h * 0.36 - p, "{name_en}", 15.0 * s, f = FontOption.SERIF, lines = 2)
                b.text(p, h * 0.36, iw, h * 0.22, "{name_ur}", 11.0 * s)
                b.line(h * 0.62, stroke = 0.2)
                b.text(p, h * 0.66, iw, h * 0.14, "EXP {exp}", 7.5 * s, bold = true, f = FontOption.SANS)
                b.text(p, h * 0.81, iw, h - p - h * 0.81, "MFG {mfg} · {price}", 6.0 * s, f = FontOption.SANS)
            }
            Layout.ROUND -> {
                val cw = iw * 0.72; val cx = (w - cw) / 2
                b.text(cx, h * 0.14, cw, h * 0.1, "{business}", 5.5 * s, bold = true, align = C, f = FontOption.SANS)
                b.text(p, h * 0.27, iw, h * 0.18, "{name_en}", 11.0 * s, bold = true, align = C)
                b.text(p, h * 0.45, iw, h * 0.17, "{name_ur}", 10.0 * s, align = C)
                b.text(cx, h * 0.64, cw, h * 0.1, "EXP {exp}", 6.5 * s, bold = true, align = C)
                b.text(cx, h * 0.75, cw, h * 0.1, "{price}", 6.5 * s, align = C)
            }
            Layout.INVERTED -> {
                b.text(0.0, 0.0, w, h * 0.2, "{business}", 7.5 * s, bold = true, align = C, inverted = true)
                b.text(p, h * 0.23, iw * 0.6, h * 0.2, "{name_en}", 11.0 * s, bold = true)
                b.text(p + iw * 0.62, h * 0.21, iw * 0.38, h * 0.24, "{name_ur}", 10.0 * s, align = E)
                b.text(p, h * 0.47, iw * 0.5, h * 0.12, "MFG {mfg}", 6.5 * s, f = FontOption.SANS)
                b.text(p + iw * 0.5, h * 0.47, iw * 0.5, h * 0.12, "EXP {exp}", 6.5 * s, bold = true, align = E, f = FontOption.SANS)
                b.barcode(p, h * 0.62, iw * 0.66, h - p - h * 0.62)
                b.text(p + iw * 0.68, h * 0.66, iw * 0.32, h * 0.25, "{price}", 9.0 * s, bold = true, align = E, f = FontOption.SANS)
            }
            Layout.MANUFACTURING -> {
                val q = min(h * 0.42, iw * 0.3)
                b.text(p, p, iw - q - 1, h * 0.2 - p, "{name_en}", 10.0 * s, bold = true)
                b.text(p, h * 0.2, iw - q - 1, h * 0.17, "{name_ur}", 9.0 * s)
                b.qr(w - p - q, p, q)
                b.text(p, h * 0.40, iw - q - 1, h * 0.1, "Batch: {batch}", 6.0 * s, f = FontOption.MONO)
                b.text(p, h * 0.50, (iw - q) / 2, h * 0.1, "MFG {mfg}", 6.0 * s, f = FontOption.MONO)
                b.text(p + (iw - q) / 2, h * 0.50, (iw - q) / 2, h * 0.1, "EXP {exp}", 6.0 * s, bold = true, f = FontOption.MONO)
                b.text(w - p - q, h * 0.50, q, h * 0.1, "{code}", 5.5 * s, align = C, f = FontOption.MONO)
                b.box(p, h * 0.62 - 0.5, iw, 0.3, filled = true)
                b.barcode(p, h * 0.66, iw * 0.7, h - p - h * 0.66)
                b.text(p + iw * 0.72, h * 0.70, iw * 0.28, h * 0.2, "{weight}", 7.0 * s, bold = true, align = E)
            }
            Layout.COMPACT -> {
                b.text(p, p * 0.6, iw, h * 0.40 - p * 0.6, "{name_en}", 7.0 * s * 1.4, bold = true, align = C, f = FontOption.SANS)
                b.text(p, h * 0.40, iw, h * 0.30, "EXP {exp}", 6.0 * s * 1.4, bold = true, align = C, f = FontOption.SANS)
                b.text(p, h * 0.70, iw, h * 0.28, "{price}", 6.0 * s * 1.4, align = C, f = FontOption.SANS)
            }
        }
        return LabelDesign(
            name = spec.name, groupName = spec.group, isTemplate = true, widthMm = w, heightMm = h,
            round = spec.layout == Layout.ROUND, borderMm = spec.border, insetMm = spec.inset, cornerMm = spec.corner,
            builtInKey = key, elements = b.els,
        )
    }

    /**
     * Re-fits a design to another label size: positions and sizes scale with the label, font sizes scale with
     * the smaller ratio so text never grows past its box. The renderer then shrinks any text that still overflows.
     */
    fun adaptTo(design: LabelDesign, w: Double, h: Double): LabelDesign {
        val rx = w / design.widthMm; val ry = h / design.heightMm; val rf = min(rx, ry)
        return design.copy(
            widthMm = w, heightMm = h,
            elements = design.elements.map {
                it.copy(
                    uid = LabelElement.nextUid(),
                    xMm = it.xMm * rx, yMm = it.yMm * ry, wMm = it.wMm * rx,
                    hMm = if (it.type == ElementType.LINE) it.hMm else it.hMm * ry,
                    fontPt = (it.fontPt * rf).coerceAtLeast(4.0),
                )
            },
        )
    }
}
