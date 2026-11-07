package com.bakery.labelapp.label

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.bakery.labelapp.data.LabelSettings
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What goes on one label. Nothing else is ever printed. */
data class LabelContent(
    val bakeryName: String,
    val subtitle: String?,
    val productName: String,
    val mfgText: String,
    val expText: String,
)

/**
 * The single drawing routine for the label. The live preview, the PDF and the
 * thermal printer image all come from here, so they always match.
 *
 * Layout (proportions taken from the 50 x 30 mm design, scaled to any size):
 *   BAKERY NAME / FRESH & QUALITY PRODUCTS / divider / PRODUCT / MFG Date / EXP Date
 */
object LabelRenderer {

    private val heavy: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /**
     * @param pxPerMm output resolution (printer: dpi / 25.4)
     * @param mono    true for thermal printers: one colour only, so every text and
     *                line is drawn in the colour that contrasts with the background
     *                (a red expiry date on black would otherwise disappear).
     */
    fun bitmap(s: LabelSettings, c: LabelContent, pxPerMm: Float, mono: Boolean): Bitmap {
        val w = max(1, (s.widthMm * pxPerMm).roundToInt())
        val h = max(1, (s.heightMm * pxPerMm).roundToInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (mono) canvas.drawColor(Color.WHITE) // the paper
        draw(canvas, s, c, pxPerMm, mono)
        return bmp
    }

    fun draw(canvas: Canvas, s: LabelSettings, c: LabelContent, pxPerMm: Float, mono: Boolean) {
        val w = s.widthMm * pxPerMm
        val h = s.heightMm * pxPerMm
        // design unit: 1 mm on the reference 50 x 30 label
        val u = pxPerMm * min(s.heightMm / 30f, s.widthMm / 50f)

        var bg = s.backgroundColor.toInt()
        var fg = s.textColor.toInt()
        var line = s.borderColor.toInt()
        var exp = s.expiryColor.toInt()
        var sub = withAlpha(fg, 0.85f)
        var divider = withAlpha(fg, 0.55f)
        if (mono) {
            val dark = Color.luminance(bg) < 0.5f
            bg = if (dark) Color.BLACK else Color.WHITE
            fg = if (dark) Color.WHITE else Color.BLACK
            line = fg; exp = fg; sub = fg; divider = fg
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = bg }
        val corner = s.cornerMm * pxPerMm
        canvas.drawRoundRect(RectF(0f, 0f, w, h), corner, corner, fill)

        // thin inner border
        val pad = 1.1f * u
        val bw = if (s.borderMm > 0f) max(1f, s.borderMm * pxPerMm) else 0f
        if (bw > 0f) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = bw; color = line
            }
            val r = max(0f, corner - pad)
            canvas.drawRoundRect(RectF(pad + bw / 2, pad + bw / 2, w - pad - bw / 2, h - pad - bw / 2), r, r, stroke)
        }

        val left = pad + bw + 2.0f * u
        val right = w - pad - bw - 2.0f * u
        val top = pad + bw + 1.3f * u
        val bottom = h - pad - bw - 1.3f * u
        val maxW = right - left
        val cx = w / 2f
        val fs = s.fontScale

        // ---- top block: bakery name, subtitle, divider
        var y = top
        val namePaint = text(heavy, 3.5f * u * fs, fg, 0.06f, Paint.Align.CENTER).also { fit(it, c.bakeryName, maxW) }
        y = drawCentered(canvas, c.bakeryName, namePaint, cx, y)
        if (!c.subtitle.isNullOrBlank()) {
            y += 0.5f * u
            val subPaint = text(medium, 1.45f * u * fs, sub, 0.14f, Paint.Align.CENTER).also { fit(it, c.subtitle, maxW) }
            y = drawCentered(canvas, c.subtitle, subPaint, cx, y)
        }
        y += 0.9f * u
        val dh = max(1f, 0.18f * u)
        val dw = maxW * 0.86f
        fill.color = divider
        canvas.drawRect(cx - dw / 2, y, cx + dw / 2, y + dh, fill)
        val dividerBottom = y + dh

        // ---- bottom block: dates (label left, value right)
        val dateSize = 2.35f * u * fs
        val labelPaint = text(bold, dateSize, fg, 0f, Paint.Align.LEFT)
        val mfgPaint = text(bold, dateSize, fg, 0f, Paint.Align.RIGHT)
        val expPaint = text(bold, dateSize, exp, 0f, Paint.Align.RIGHT)
        val longest = max(labelPaint.measureText("EXP Date:") + mfgPaint.measureText(c.mfgText),
            labelPaint.measureText("EXP Date:") + expPaint.measureText(c.expText)) + 1.5f * u
        if (longest > maxW) {
            val k = maxW / longest
            listOf(labelPaint, mfgPaint, expPaint).forEach { it.textSize *= k }
        }
        val fm = labelPaint.fontMetrics
        val rowH = fm.descent - fm.ascent
        val row2 = bottom - fm.descent
        val row1 = row2 - rowH - 0.35f * u
        canvas.drawText("MFG Date:", left, row1, labelPaint)
        canvas.drawText(c.mfgText, right, row1, mfgPaint)
        canvas.drawText("EXP Date:", left, row2, labelPaint)
        canvas.drawText(c.expText, right, row2, expPaint)
        val datesTop = row1 + fm.ascent

        // ---- product name, centred in the space between
        val prodPaint = text(heavy, 3.6f * u * fs, fg, 0.03f, Paint.Align.CENTER).also { fit(it, c.productName, maxW) }
        val space = datesTop - dividerBottom
        val pfm = prodPaint.fontMetrics
        if (pfm.descent - pfm.ascent > space) prodPaint.textSize *= space / (pfm.descent - pfm.ascent)
        val pm = prodPaint.fontMetrics
        val mid = (dividerBottom + datesTop) / 2f
        canvas.drawText(c.productName, cx, mid - (pm.ascent + pm.descent) / 2f, prodPaint)
    }

    private fun text(tf: Typeface, size: Float, color: Int, spacing: Float, align: Paint.Align) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf; textSize = size; this.color = color
            letterSpacing = spacing; textAlign = align; isSubpixelText = true
        }

    /** Shrinks the text until it fits the width (long product names). */
    private fun fit(p: Paint, text: String, maxW: Float) {
        while (p.measureText(text) > maxW && p.textSize > 4f) p.textSize *= 0.95f
    }

    /** Draws centred text whose top is at [top]; returns its bottom. */
    private fun drawCentered(canvas: Canvas, text: String, p: Paint, cx: Float, top: Float): Float {
        val fm = p.fontMetrics
        val baseline = top - fm.ascent
        canvas.drawText(text, cx, baseline, p)
        return baseline + fm.descent
    }

    private fun withAlpha(color: Int, a: Float): Int =
        Color.argb((Color.alpha(color) * a).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))
}
