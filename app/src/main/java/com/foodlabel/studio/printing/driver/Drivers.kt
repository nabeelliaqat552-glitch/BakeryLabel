package com.foodlabel.studio.printing.driver

import java.io.ByteArrayOutputStream

/**
 * 1-bit image, row-major, MSB first. Bit value 1 = black dot.
 * Width/height are printer dots at the job's DPI, so 50 mm at 203 DPI = 400 dots.
 */
class MonoBitmap(val width: Int, val height: Int, val data: ByteArray = ByteArray(((width + 7) / 8) * height)) {
    val bytesPerRow: Int get() = (width + 7) / 8

    init {
        require(width > 0 && height > 0) { "Empty image" }
        require(data.size == bytesPerRow * height) { "Data size does not match image size" }
    }

    fun set(x: Int, y: Int, black: Boolean) {
        val i = y * bytesPerRow + x / 8
        val mask = (0x80 ushr (x % 8))
        data[i] = if (black) (data[i].toInt() or mask).toByte() else (data[i].toInt() and mask.inv()).toByte()
    }

    fun isBlack(x: Int, y: Int): Boolean =
        (data[y * bytesPerRow + x / 8].toInt() and (0x80 ushr (x % 8))) != 0

    /** Copy of rows [from, to). */
    fun rows(from: Int, to: Int): ByteArray = data.copyOfRange(from * bytesPerRow, to * bytesPerRow)
}

/** Printer command languages the app speaks. */
enum class PrinterProtocol(val label: String, val description: String) {
    TSPL("TSPL / TSPL2", "TSC and many Xprinter, Gprinter, iDPRT label printers"),
    ESC_POS("ESC/POS", "Receipt-style thermal printers, including many 58/80 mm Bluetooth printers"),
    ZPL("ZPL II", "Zebra and Zebra-compatible label printers"),
    CPCL("CPCL", "Mobile printers that use CPCL (some Zebra, Honeywell and others)"),
    ANDROID_PRINT("Android Print", "Any printer with an installed Android print service (sent as an exact-size PDF)");

    val isRaw: Boolean get() = this != ANDROID_PRINT

    companion object {
        fun parse(value: String?): PrinterProtocol = entries.firstOrNull { it.name == value } ?: TSPL
    }
}

/** Everything a driver needs to produce the bytes for one label design printed [copies] times. */
data class RasterJob(
    val image: MonoBitmap,
    val dpi: Int,
    val labelWidthMm: Double,
    val labelHeightMm: Double,
    val gapMm: Double,
    val copies: Int,
    /** 0..15 (mapped per protocol). */
    val density: Int = 8,
    /** 1..6 inch per second (mapped per protocol). */
    val speed: Int = 3,
    val continuousPaper: Boolean = false,
    /** Horizontal offset in dots to centre a narrow label on a wider print head. */
    val offsetXDots: Int = 0,
)

interface PrinterDriver {
    val protocol: PrinterProtocol
    fun build(job: RasterJob): ByteArray
}

object Drivers {
    fun forProtocol(p: PrinterProtocol): PrinterDriver = when (p) {
        PrinterProtocol.TSPL -> TsplDriver
        PrinterProtocol.ESC_POS -> EscPosDriver
        PrinterProtocol.ZPL -> ZplDriver
        PrinterProtocol.CPCL -> CpclDriver
        PrinterProtocol.ANDROID_PRINT -> error("Android Print does not use a raw driver")
    }
}

private fun ByteArrayOutputStream.ascii(s: String) = write(s.toByteArray(Charsets.US_ASCII))

private fun hex(bytes: ByteArray): String {
    val digits = "0123456789ABCDEF"
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        sb.append(digits[v ushr 4]).append(digits[v and 0x0F])
    }
    return sb.toString()
}

private fun mm(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.ENGLISH, "%.1f", v)

/** TSPL: label printers with gap/black-mark sensing. In TSPL BITMAP data, a 0 bit prints a dot. */
object TsplDriver : PrinterDriver {
    override val protocol = PrinterProtocol.TSPL
    override fun build(job: RasterJob): ByteArray {
        val out = ByteArrayOutputStream()
        val img = job.image
        out.ascii("SIZE ${mm(job.labelWidthMm)} mm,${mm(job.labelHeightMm)} mm\r\n")
        if (job.continuousPaper) out.ascii("GAP 0 mm,0 mm\r\n") else out.ascii("GAP ${mm(job.gapMm)} mm,0 mm\r\n")
        out.ascii("DIRECTION 1,0\r\n")
        out.ascii("REFERENCE 0,0\r\n")
        out.ascii("DENSITY ${job.density.coerceIn(0, 15)}\r\n")
        out.ascii("SPEED ${job.speed.coerceIn(1, 6)}\r\n")
        out.ascii("CLS\r\n")
        out.ascii("BITMAP ${job.offsetXDots.coerceAtLeast(0)},0,${img.bytesPerRow},${img.height},0,")
        val inverted = ByteArray(img.data.size) { (img.data[it].toInt() xor 0xFF).toByte() }
        out.write(inverted)
        out.ascii("\r\n")
        out.ascii("PRINT ${job.copies.coerceAtLeast(1)},1\r\n")
        return out.toByteArray()
    }
}

/**
 * ESC/POS raster (GS v 0). Receipt printers have no label size or gap commands, so each copy
 * is printed as an image followed by the configured gap feed. Images are sent in bands
 * because many low-cost printers have small input buffers.
 */
object EscPosDriver : PrinterDriver {
    override val protocol = PrinterProtocol.ESC_POS
    private const val BAND_ROWS = 128

    override fun build(job: RasterJob): ByteArray {
        val out = ByteArrayOutputStream()
        val img = job.image
        out.write(byteArrayOf(0x1B, 0x40)) // ESC @ initialise
        out.write(byteArrayOf(0x1B, 0x33, 0x00)) // ESC 3 0: no extra line spacing
        val shift = job.offsetXDots.coerceAtLeast(0)
        val shiftedBytesPerRow = (img.width + shift + 7) / 8
        val feedDots = if (job.continuousPaper) 0 else (job.gapMm * job.dpi / 25.4).toInt()
        repeat(job.copies.coerceAtLeast(1)) {
            var y = 0
            while (y < img.height) {
                val rows = minOf(BAND_ROWS, img.height - y)
                out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00))
                out.write(shiftedBytesPerRow and 0xFF); out.write((shiftedBytesPerRow shr 8) and 0xFF)
                out.write(rows and 0xFF); out.write((rows shr 8) and 0xFF)
                out.write(if (shift == 0) img.rows(y, y + rows) else shiftRows(img, y, rows, shift, shiftedBytesPerRow))
                y += rows
            }
            var remaining = feedDots
            while (remaining > 0) { // ESC J n: feed n dots
                val n = minOf(255, remaining)
                out.write(byteArrayOf(0x1B, 0x4A, n.toByte()))
                remaining -= n
            }
        }
        return out.toByteArray()
    }

    private fun shiftRows(img: MonoBitmap, y0: Int, rows: Int, shift: Int, bpr: Int): ByteArray {
        val shifted = MonoBitmap(img.width + shift, rows, ByteArray(bpr * rows))
        for (y in 0 until rows) for (x in 0 until img.width) if (img.isBlack(x, y0 + y)) shifted.set(x + shift, y, true)
        return shifted.data
    }
}

/** ZPL II with a ^GFA graphic field (1 bit = black). */
object ZplDriver : PrinterDriver {
    override val protocol = PrinterProtocol.ZPL
    override fun build(job: RasterJob): ByteArray {
        val img = job.image
        val total = img.data.size
        val darkness = (job.density.coerceIn(0, 15) * 2) // ZPL ~SD range 00..30
        val sb = StringBuilder()
        sb.append("~SD").append(darkness.toString().padStart(2, '0')).append("\n")
        sb.append("^XA\n")
        sb.append(if (job.continuousPaper) "^MNN\n" else "^MNY\n")
        sb.append("^PR").append(job.speed.coerceIn(1, 6)).append("\n")
        sb.append("^PW").append(img.width + job.offsetXDots.coerceAtLeast(0)).append("\n")
        sb.append("^LL").append(img.height).append("\n")
        sb.append("^LH0,0\n")
        sb.append("^FO").append(job.offsetXDots.coerceAtLeast(0)).append(",0")
        sb.append("^GFA,").append(total).append(',').append(total).append(',').append(img.bytesPerRow).append(',')
        sb.append(hex(img.data)).append("^FS\n")
        sb.append("^PQ").append(job.copies.coerceAtLeast(1)).append("\n")
        sb.append("^XZ\n")
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }
}

/** CPCL with an EG (expanded graphics) command; FORM advances to the next label gap. */
object CpclDriver : PrinterDriver {
    override val protocol = PrinterProtocol.CPCL
    override fun build(job: RasterJob): ByteArray {
        val img = job.image
        val sb = StringBuilder()
        sb.append("! 0 ").append(job.dpi).append(' ').append(job.dpi).append(' ')
            .append(img.height).append(' ').append(job.copies.coerceAtLeast(1)).append("\r\n")
        sb.append("PAGE-WIDTH ").append(img.width + job.offsetXDots.coerceAtLeast(0)).append("\r\n")
        sb.append("CONTRAST ").append((job.density.coerceIn(0, 15) / 5).coerceIn(0, 3)).append("\r\n")
        sb.append("SPEED ").append(job.speed.coerceIn(0, 5)).append("\r\n")
        sb.append("EG ").append(img.bytesPerRow).append(' ').append(img.height).append(' ')
            .append(job.offsetXDots.coerceAtLeast(0)).append(" 0 ").append(hex(img.data)).append("\r\n")
        if (!job.continuousPaper) sb.append("FORM\r\n")
        sb.append("PRINT\r\n")
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }
}
