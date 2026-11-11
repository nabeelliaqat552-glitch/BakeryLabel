package com.foodlabel.studio.label

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.LocaleList
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.LruCache
import com.foodlabel.studio.barcode.BarcodeEncoder
import com.foodlabel.studio.barcode.BarcodeType
import com.foodlabel.studio.core.Units
import com.foodlabel.studio.printing.driver.MonoBitmap
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class RenderResult(
    val bitmap: Bitmap,
    /** Plain-language problems: text shrunk to the minimum, barcode too thin, and so on. */
    val warnings: List<String>,
    /** uids of elements that did not fit their box. */
    val problemElements: Set<Long>,
    val dpi: Int,
    val scale: Int,
)

/**
 * Draws a label at a real printer resolution. The same code produces the on-screen preview and the
 * printed image, so what the user sees is what prints.
 *
 * Size in dots = size in mm × dpi / 25.4. Nothing is stretched to fit the paper: a 50 × 30 mm label at
 * 203 DPI is always 400 × 240 dots.
 *
 * Text is laid out with Android's StaticLayout, which applies full Unicode bidi and HarfBuzz shaping,
 * so Urdu letters join correctly. Urdu text requests the "ur" locale, which makes Android choose a
 * Nastaliq font where the device has one (Noto Nastaliq Urdu ships with Android 9+) and Naskh otherwise.
 */
object LabelRenderer {

    const val MIN_FONT_PT = 4.0
    /** Narrowest bar we consider reliably scannable, in mm. */
    const val MIN_MODULE_MM = 0.19

    private val logoCache = LruCache<String, Bitmap>(8)

    fun render(design: LabelDesign, data: LabelData, dpi: Int, scale: Int = 1, forPrint: Boolean = false): RenderResult {
        val pxPerMm = dpi * scale / Units.MM_PER_INCH
        val w = max(1, (design.widthMm * pxPerMm).roundToInt())
        val h = max(1, (design.heightMm * pxPerMm).roundToInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fg = if (design.backgroundBlack) Color.WHITE else Color.BLACK
        val bg = if (design.backgroundBlack) Color.BLACK else Color.WHITE
        c.drawColor(bg)

        val warnings = mutableListOf<String>()
        val problems = mutableSetOf<Long>()

        if (design.borderMm > 0) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; color = fg; strokeWidth = max(1f, (design.borderMm * pxPerMm).toFloat())
            }
            val inset = (design.insetMm * 0.5 * pxPerMm).toFloat() + p.strokeWidth / 2
            val r = RectF(inset, inset, w - inset, h - inset)
            if (design.round) c.drawOval(r, p)
            else {
                val cr = (design.cornerMm * pxPerMm).toFloat()
                c.drawRoundRect(r, cr, cr, p)
            }
        }

        for (e in design.elements.sortedBy { it.z }) {
            if (Tokens.isHidden(e, data)) continue
            val box = RectF(
                (e.xMm * pxPerMm).toFloat(), (e.yMm * pxPerMm).toFloat(),
                ((e.xMm + e.wMm) * pxPerMm).toFloat(), ((e.yMm + e.hMm) * pxPerMm).toFloat(),
            )
            if (box.width() < 1f || box.height() < 0.5f) continue
            c.save()
            if (e.rotation % 360 != 0) c.rotate(e.rotation.toFloat(), box.centerX(), box.centerY())
            val ok = when (e.type) {
                ElementType.TEXT -> drawText(c, e, Tokens.resolve(e.content, data), box, dpi * scale, fg, bg, warnings)
                ElementType.BARCODE -> drawBarcode(c, e, data, box, dpi, scale, fg, bg, warnings)
                ElementType.QR -> drawQr(c, e, Tokens.resolve(e.content, data), box, dpi, scale, fg, warnings)
                ElementType.IMAGE -> drawImage(c, e.imagePath ?: data.logoPath, box)
                ElementType.LINE -> { c.drawRect(box.left, box.top, box.right, box.top + max(1f, (e.strokeMm * pxPerMm).toFloat()), fill(fg)); true }
                ElementType.BOX -> {
                    if (e.inverted) c.drawRect(box, fill(fg))
                    else c.drawRect(box, Paint().apply { style = Paint.Style.STROKE; color = fg; strokeWidth = max(1f, (e.strokeMm * pxPerMm).toFloat()) })
                    true
                }
            }
            c.restore()
            if (!ok) problems += e.uid
        }
        if (!forPrint && design.round) {
            // Show the die-cut outline on screen only.
            val outside = Path().apply {
                fillType = Path.FillType.INVERSE_EVEN_ODD
                addOval(RectF(0f, 0f, w.toFloat(), h.toFloat()), Path.Direction.CW)
            }
            c.drawPath(outside, fill(Color.argb(40, 0, 0, 0)))
        }
        return RenderResult(bmp, warnings.distinct(), problems, dpi, scale)
    }

    private fun fill(color: Int) = Paint().apply { style = Paint.Style.FILL; this.color = color }

    private fun hasArabicScript(s: String) = s.any { Character.UnicodeBlock.of(it).let { b ->
        b == Character.UnicodeBlock.ARABIC || b == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A ||
            b == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B || b == Character.UnicodeBlock.ARABIC_SUPPLEMENT
    } }

    fun typeface(font: FontOption, bold: Boolean, italic: Boolean): Typeface {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val base = when (font) {
            FontOption.SANS -> Typeface.SANS_SERIF
            FontOption.SERIF -> Typeface.SERIF
            FontOption.MONO -> Typeface.MONOSPACE
            FontOption.CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        }
        return Typeface.create(base, style)
    }

    private fun layout(text: String, paint: TextPaint, width: Int, align: Layout.Alignment, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, width))
            .setAlignment(align)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(null)
            .setLineSpacing(0f, 1f)
            .build()

    /** Shrinks the font until the text fits the box without breaking words; never draws outside the box. */
    private fun drawText(c: Canvas, e: LabelElement, text: String, box: RectF, dpiScaled: Int, fg: Int, bg: Int, warnings: MutableList<String>): Boolean {
        if (text.isBlank()) return true
        val urdu = hasArabicScript(text)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = typeface(e.font, e.bold, e.italic)
            color = if (e.inverted) bg else fg
            if (urdu) textLocales = LocaleList(Locale("ur", "PK"), Locale.ENGLISH)
        }
        if (e.inverted) c.drawRect(box, fill(fg))
        val pad = if (e.inverted) box.height() * 0.08f else 0f
        val availW = (box.width() - 2 * pad).toInt()
        val availH = box.height() - 2 * pad
        val align = when (e.align) {
            TextAlignOption.START -> Layout.Alignment.ALIGN_NORMAL
            TextAlignOption.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignOption.END -> Layout.Alignment.ALIGN_OPPOSITE
        }
        var pt = e.fontPt + 0.5
        var fits: Boolean
        var lay: StaticLayout
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        do {
            pt = max(MIN_FONT_PT, pt - 0.5)
            paint.textSize = Units.ptToDots(pt, dpiScaled).toFloat()
            lay = layout(text, paint, availW, align, max(1, e.maxLines))
            val fullLayout = layout(text, paint, availW, align, Int.MAX_VALUE)
            val longestWord = words.maxOfOrNull { paint.measureText(it) } ?: 0f
            fits = fullLayout.lineCount <= max(1, e.maxLines) && lay.height <= availH + 0.5f && longestWord <= availW + 0.5f
        } while (!fits && pt > MIN_FONT_PT)
        if (!fits) warnings += "\"${text.take(24)}\" is too long for its box. Make the box bigger or the text shorter."
        c.save()
        c.clipRect(box)
        val dy = box.top + pad + max(0f, (availH - lay.height) / 2f)
        c.translate(box.left + pad, dy)
        lay.draw(c)
        c.restore()
        return fits
    }

    private fun drawBarcode(c: Canvas, e: LabelElement, data: LabelData, box: RectF, dpi: Int, scale: Int, fg: Int, bg: Int, warnings: MutableList<String>): Boolean {
        val usesProduct = e.content.isBlank() || e.content.contains("{barcode}")
        val type = if (usesProduct) data.barcodeType else e.barcodeType
        val value = Tokens.resolve(e.content.ifBlank { "{barcode}" }, data)
        if (type == BarcodeType.QR_CODE) return drawQr(c, e, value, box, dpi, scale, fg, warnings)
        val matrix = BarcodeEncoder.encode(type, value).getOrElse {
            warnings += "Barcode: ${it.message}"
            drawErrorBox(c, box, fg)
            return false
        }
        val hrText = if (e.humanReadable) BarcodeEncoder.humanReadable(type, value) else ""
        val hrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL); color = fg
            textSize = Units.ptToDots(max(4.0, e.fontPt), dpi * scale).toFloat()
            textAlign = Paint.Align.CENTER
        }
        val hrH = if (hrText.isNotEmpty()) hrPaint.fontSpacing else 0f
        val barsH = box.height() - hrH
        if (barsH < 2f) { warnings += "Barcode box is too short."; drawErrorBox(c, box, fg); return false }

        val modules = matrix.width
        val boxDots = box.width() / scale
        val moduleDots = floor(boxDots / modules).toInt()
        var ok = true
        val modulePx: Float
        if (moduleDots < 1) {
            warnings += "Barcode does not fit its box. Make the barcode wider or use a shorter number."
            modulePx = box.width() / modules
            ok = false
        } else {
            modulePx = (moduleDots * scale).toFloat()
            val moduleMm = moduleDots * Units.MM_PER_INCH / dpi
            if (moduleMm < MIN_MODULE_MM) {
                warnings += "Barcode bars are only ${"%.2f".format(Locale.ENGLISH, moduleMm)} mm wide and may not scan. Make the barcode wider."
                ok = false
            }
        }
        val totalW = modulePx * modules
        val left = box.left + (box.width() - totalW) / 2f
        val p = fill(fg)
        var x = 0
        while (x < modules) {
            if (matrix.get(x, 0)) {
                var run = 1
                while (x + run < modules && matrix.get(x + run, 0)) run++
                c.drawRect(left + x * modulePx, box.top, left + (x + run) * modulePx, box.top + barsH, p)
                x += run
            } else x++
        }
        if (hrText.isNotEmpty()) {
            c.drawText(hrText, box.centerX(), box.top + barsH + hrH - hrPaint.descent(), hrPaint)
        }
        return ok
    }

    private fun drawQr(c: Canvas, e: LabelElement, value: String, box: RectF, dpi: Int, scale: Int, fg: Int, warnings: MutableList<String>): Boolean {
        val matrix = BarcodeEncoder.encode(BarcodeType.QR_CODE, value).getOrElse {
            warnings += "QR code: ${it.message}"; drawErrorBox(c, box, fg); return false
        }
        val n = matrix.width
        val side = min(box.width(), box.height())
        val moduleDots = floor(side / scale / n).toInt()
        var ok = true
        val modulePx = if (moduleDots < 1) { ok = false; warnings += "QR code is too small for its content. Make it bigger or shorten the text."; side / n }
        else (moduleDots * scale).toFloat()
        if (moduleDots in 1..1 && dpi < 300) { warnings += "QR code modules are tiny and may not scan. Make it bigger."; ok = false }
        val total = modulePx * n
        val left = box.left + (box.width() - total) / 2f
        val top = box.top + (box.height() - total) / 2f
        val p = fill(fg)
        for (y in 0 until n) for (x in 0 until n) if (matrix.get(x, y)) {
            c.drawRect(left + x * modulePx, top + y * modulePx, left + (x + 1) * modulePx, top + (y + 1) * modulePx, p)
        }
        return ok
    }

    private fun drawImage(c: Canvas, path: String?, box: RectF): Boolean {
        if (path == null) return true
        val bmp = logoCache.get(path) ?: BitmapFactory.decodeFile(path)?.also { logoCache.put(path, it) } ?: return false
        val s = min(box.width() / bmp.width, box.height() / bmp.height)
        val dw = bmp.width * s; val dh = bmp.height * s
        val dst = RectF(box.centerX() - dw / 2, box.centerY() - dh / 2, box.centerX() + dw / 2, box.centerY() + dh / 2)
        c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        return true
    }

    private fun drawErrorBox(c: Canvas, box: RectF, fg: Int) {
        val p = Paint().apply { style = Paint.Style.STROKE; color = fg; strokeWidth = 2f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f) }
        c.drawRect(box, p)
        c.drawLine(box.left, box.top, box.right, box.bottom, p)
    }

    /**
     * Converts the rendered label to printer dots. Dark pixels (luminance below [threshold]) become black.
     * [offsetDots] shifts the image right on a print head wider than the label.
     */
    fun toMono(bitmap: Bitmap, threshold: Int = 150): MonoBitmap {
        val w = bitmap.width; val h = bitmap.height
        val mono = MonoBitmap(w, h)
        val row = IntArray(w)
        for (y in 0 until h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val px = row[x]
                val a = Color.alpha(px)
                val lum = (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
                val effective = 255 - ((255 - lum) * a / 255)
                if (effective < threshold) mono.set(x, y, true)
            }
        }
        return mono
    }
}
