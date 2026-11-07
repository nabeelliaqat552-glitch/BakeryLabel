package com.bakery.labelapp.print

import android.app.Activity
import android.content.Context
import com.bakery.labelapp.data.LabelSettings
import com.bakery.labelapp.label.LabelContent
import com.bakery.labelapp.label.LabelRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LabelPrintService {

    suspend fun print(activity: Activity, cfg: PrinterConfig, s: LabelSettings, c: LabelContent, copies: Int) {
        require(copies in 1..999) { "Copies must be between 1 and 999" }
        if (cfg.type == PrinterType.SYSTEM) {
            val file = withContext(Dispatchers.IO) { PdfLabels.create(activity, s, c, copies) }
            withContext(Dispatchers.Main) { PdfLabels.printWithSystem(activity, s, file) }
            return
        }
        require(cfg.isConfigured) { "Choose a printer first (tap the printer bar)" }
        val bytes = withContext(Dispatchers.Default) { buildCommands(cfg, s, c, copies) }
        transportFor(activity.applicationContext, cfg).send(bytes)
    }

    fun buildCommands(cfg: PrinterConfig, s: LabelSettings, c: LabelContent, copies: Int): ByteArray {
        val dotsPerMm = cfg.dpi / 25.4f
        val bmp = LabelRenderer.bitmap(s, c, dotsPerMm, mono = true)
        var packed = Raster.pack(bmp)
        bmp.recycle()
        if (cfg.invert) packed = packed.inverted()
        return when (cfg.language) {
            PrinterLanguage.TSPL -> Commands.tspl(s, packed, copies)
            PrinterLanguage.ESCPOS -> Commands.escpos(packed, copies)
        }
    }

    suspend fun test(context: Context, cfg: PrinterConfig) {
        require(cfg.isConfigured) { "Choose a printer first" }
        transportFor(context, cfg).test()
    }
}
