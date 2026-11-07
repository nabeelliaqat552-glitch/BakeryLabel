package com.bakery.labelapp.print

import android.graphics.Bitmap
import android.graphics.Color
import com.bakery.labelapp.data.LabelSettings
import com.bakery.labelapp.label.mm
import java.io.ByteArrayOutputStream

/** 1-bit image, 8 dots per byte, most significant bit first. Bit 1 = black dot. */
class Packed(val widthBytes: Int, val height: Int, val data: ByteArray) {
    fun inverted() = Packed(widthBytes, height, ByteArray(data.size) { (data[it].toInt() xor 0xFF).toByte() })
}

object Raster {
    fun pack(bmp: Bitmap): Packed {
        val w = bmp.width
        val h = bmp.height
        val wb = (w + 7) / 8
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val out = ByteArray(wb * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = px[y * w + x]
                if (Color.alpha(c) < 128) continue // transparent = paper
                val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                if (lum < 128) {
                    val i = y * wb + (x shr 3)
                    out[i] = (out[i].toInt() or (0x80 ushr (x and 7))).toByte()
                }
            }
        }
        return Packed(wb, h, out)
    }
}

object Commands {

    /** TSPL label printers. Copies are handled by the printer: PRINT 1,n. */
    fun tspl(s: LabelSettings, p: Packed, copies: Int): ByteArray {
        val out = ByteArrayOutputStream()
        fun line(t: String) = out.write("$t\r\n".toByteArray(Charsets.US_ASCII))
        line("SIZE ${mm(s.widthMm)} mm,${mm(s.heightMm)} mm")
        line("GAP ${mm(s.gapMm)} mm,0 mm")
        line("DIRECTION 1")
        line("REFERENCE 0,0")
        line("CLS")
        // In TSPL bitmaps a 0 bit prints, so the image is inverted here.
        out.write("BITMAP 0,0,${p.widthBytes},${p.height},0,".toByteArray(Charsets.US_ASCII))
        out.write(p.inverted().data)
        out.write("\r\n".toByteArray(Charsets.US_ASCII))
        line("PRINT 1,$copies")
        return out.toByteArray()
    }

    /** ESC/POS raster image (GS v 0), repeated once per copy. */
    fun escpos(p: Packed, copies: Int): ByteArray {
        val out = ByteArrayOutputStream()
        repeat(copies) {
            out.write(byteArrayOf(0x1B, 0x40)) // ESC @ init
            out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00)) // GS v 0, normal
            out.write(p.widthBytes and 0xFF); out.write((p.widthBytes shr 8) and 0xFF)
            out.write(p.height and 0xFF); out.write((p.height shr 8) and 0xFF)
            out.write(p.data)
            out.write(byteArrayOf(0x1B, 0x64, 0x03)) // feed 3 lines
        }
        return out.toByteArray()
    }
}
