package com.bakery.labelapp.print

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.bakery.labelapp.data.LabelSettings
import com.bakery.labelapp.label.LabelContent
import com.bakery.labelapp.label.LabelRenderer
import com.bakery.labelapp.label.mm
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.roundToInt

/** PDF export (one page per copy, page = exact label size) and Android system printing. */
object PdfLabels {
    private const val PT_PER_MM = 72f / 25.4f

    fun create(context: Context, s: LabelSettings, c: LabelContent, copies: Int): File {
        val doc = PdfDocument()
        val wPt = (s.widthMm * PT_PER_MM).roundToInt()
        val hPt = (s.heightMm * PT_PER_MM).roundToInt()
        repeat(copies) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(wPt, hPt, i + 1).create())
            LabelRenderer.draw(page.canvas, s, c, PT_PER_MM, mono = false)
            doc.finishPage(page)
        }
        val dir = File(context.cacheDir, "labels").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val safe = c.productName.lowercase().replace(Regex("[^a-z0-9]+"), "_")
        val file = File(dir, "label_${safe}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun share(activity: Activity, file: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, "Save or share label PDF"))
    }

    /** Opens Android's print dialog (any installed print service, or Save as PDF). Main thread. */
    fun printWithSystem(activity: Activity, s: LabelSettings, file: File) {
        val pm = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val media = PrintAttributes.MediaSize(
            "label_${mm(s.widthMm)}x${mm(s.heightMm)}",
            "Label ${mm(s.widthMm)} x ${mm(s.heightMm)} mm",
            (s.widthMm / 25.4f * 1000).roundToInt(),
            (s.heightMm / 25.4f * 1000).roundToInt(),
        )
        val attrs = PrintAttributes.Builder()
            .setMediaSize(media)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        pm.print("Bakery label", FilePrintAdapter(file), attrs)
    }
}

private class FilePrintAdapter(private val file: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
        val info = PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?, destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?, callback: WriteResultCallback,
    ) {
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}
