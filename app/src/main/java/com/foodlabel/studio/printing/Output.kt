package com.foodlabel.studio.printing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.foodlabel.studio.barcode.BarcodeType
import com.foodlabel.studio.core.Units
import com.foodlabel.studio.data.db.PrinterEntity
import com.foodlabel.studio.label.ElementType
import com.foodlabel.studio.label.FontOption
import com.foodlabel.studio.label.LabelData
import com.foodlabel.studio.label.LabelDesign
import com.foodlabel.studio.label.LabelElement
import com.foodlabel.studio.label.LabelRenderer
import com.foodlabel.studio.label.TextAlignOption
import com.foodlabel.studio.label.Tokens
import com.foodlabel.studio.printing.connection.ConnectionType
import com.foodlabel.studio.printing.connection.connectionFor
import com.foodlabel.studio.printing.driver.PrinterProtocol
import java.io.FileOutputStream
import java.io.OutputStream

/** PDF, PNG and Android Print output. Pages are exactly the label size; nothing is scaled to the paper. */
object Output {

    const val EXPORT_DPI = 300

    /** Writes one page per label copy (capped) at the exact physical size. */
    fun writePdf(labels: List<PreparedLabel>, out: OutputStream, maxPages: Int = 500) {
        val doc = PdfDocument()
        try {
            var page = 0
            loop@ for (l in labels) {
                val r = LabelRenderer.render(l.design, l.data, EXPORT_DPI, 1, forPrint = true)
                val wPt = Units.mmToPoints(l.design.widthMm).toFloat()
                val hPt = Units.mmToPoints(l.design.heightMm).toFloat()
                repeat(l.copies) {
                    if (page >= maxPages) return@repeat
                    page++
                    val info = PdfDocument.PageInfo.Builder(Math.round(wPt), Math.round(hPt), page).create()
                    val p = doc.startPage(info)
                    p.canvas.drawBitmap(r.bitmap, null, RectF(0f, 0f, wPt, hPt), Paint(Paint.FILTER_BITMAP_FLAG))
                    doc.finishPage(p)
                }
                r.bitmap.recycle()
                if (page >= maxPages) break@loop
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    fun writePng(design: LabelDesign, data: LabelData, out: OutputStream, dpi: Int = EXPORT_DPI) {
        val r = LabelRenderer.render(design, data, dpi, 1, forPrint = true)
        r.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        r.bitmap.recycle()
    }

    /**
     * Opens the system print dialog with exact-size pages. [onResult] gets true when the print framework
     * accepted the document (the printer itself still has to print it).
     */
    fun printWithAndroid(context: Context, jobName: String, labels: List<PreparedLabel>, onResult: (Boolean, String?) -> Unit) {
        val first = labels.firstOrNull() ?: return onResult(false, "Nothing to print.")
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val media = PrintAttributes.MediaSize(
            "fls_${first.design.widthMm}x${first.design.heightMm}",
            "Label ${Units.formatMm(first.design.widthMm)} × ${Units.formatMm(first.design.heightMm)} mm",
            Units.mmToMils(first.design.widthMm), Units.mmToMils(first.design.heightMm),
        )
        val attrs = PrintAttributes.Builder()
            .setMediaSize(media)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
                if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(labels.sumOf { it.copies }.coerceAtMost(500))
                    .build()
                cb.onLayoutFinished(info, old != new)
            }

            override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
                try {
                    FileOutputStream(dest.fileDescriptor).use { writePdf(labels, it) }
                    cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    onResult(true, null)
                } catch (e: Exception) {
                    cb.onWriteFailed(e.message)
                    onResult(false, e.message)
                }
            }
        }
        pm.print(jobName, adapter, attrs)
    }

    /** A calibration label: outline, 5 mm ticks, size text, Urdu sample and a barcode. */
    fun calibrationDesign(widthMm: Double, heightMm: Double, dpi: Int): LabelDesign {
        val els = mutableListOf<LabelElement>()
        els += LabelElement(type = ElementType.BOX, xMm = 0.0, yMm = 0.0, wMm = widthMm, hMm = heightMm, strokeMm = 0.25)
        var x = 5.0
        while (x < widthMm) {
            els += LabelElement(type = ElementType.BOX, xMm = x, yMm = 0.0, wMm = 0.2, hMm = if (x % 10.0 == 0.0) 3.0 else 1.5, inverted = true)
            x += 5.0
        }
        var y = 5.0
        while (y < heightMm) {
            els += LabelElement(type = ElementType.BOX, xMm = 0.0, yMm = y, wMm = if (y % 10.0 == 0.0) 3.0 else 1.5, hMm = 0.2, inverted = true)
            y += 5.0
        }
        val pad = 4.0
        els += LabelElement(type = ElementType.TEXT, xMm = pad, yMm = heightMm * 0.12, wMm = widthMm - 2 * pad, hMm = heightMm * 0.18,
            content = "${Units.formatMm(widthMm)} × ${Units.formatMm(heightMm)} mm · $dpi DPI", fontPt = 9.0, bold = true, align = TextAlignOption.CENTER)
        els += LabelElement(type = ElementType.TEXT, xMm = pad, yMm = heightMm * 0.32, wMm = widthMm - 2 * pad, hMm = heightMm * 0.22,
            content = "تازہ کریم · گلاب جامن", fontPt = 10.0, align = TextAlignOption.CENTER, font = FontOption.SANS)
        els += LabelElement(type = ElementType.BARCODE, xMm = pad, yMm = heightMm * 0.58, wMm = widthMm - 2 * pad, hMm = heightMm * 0.36,
            content = "{barcode}", fontPt = 6.0)
        return LabelDesign(name = "Test label", widthMm = widthMm, heightMm = heightMm, elements = els)
    }

    val calibrationData = Tokens.SAMPLE.copy(barcode = "FLS-TEST-123", barcodeType = BarcodeType.CODE_128)

    /** Sends one calibration label straight to a raw printer. Success means "sent", not "printed". */
    suspend fun sendTestLabel(context: Context, printer: PrinterEntity, widthMm: Double, heightMm: Double) {
        val protocol = PrinterProtocol.parse(printer.protocol)
        val type = ConnectionType.parse(printer.connection)
        val label = PreparedLabel(
            calibrationDesign(widthMm, heightMm, printer.dpi), calibrationData, 1,
            com.foodlabel.studio.data.db.PrintHistoryEntity(jobId = 0, businessId = printer.businessId, productId = null,
                productName = "Test label", copies = 1, mfgDate = "", expDate = "", batchNumber = "", designName = "Test"),
        )
        val bytes = PrintQueue.buildBytes(label, printer, protocol)
        val conn = connectionFor(context, type, printer.address, printer.port)
        try { conn.open(); conn.write(bytes) } finally { runCatching { conn.close() } }
    }
}
