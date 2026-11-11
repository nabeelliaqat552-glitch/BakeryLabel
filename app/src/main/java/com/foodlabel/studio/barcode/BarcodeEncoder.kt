package com.foodlabel.studio.barcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Produces the module pattern of a barcode with one pixel per module and no quiet zone,
 * so the renderer can scale it by a whole number of printer dots per module.
 * Whole-dot modules are what keep thermal-printed barcodes scannable.
 */
object BarcodeEncoder {

    fun format(type: BarcodeType): BarcodeFormat = when (type) {
        BarcodeType.CODE_128 -> BarcodeFormat.CODE_128
        BarcodeType.CODE_39 -> BarcodeFormat.CODE_39
        BarcodeType.EAN_13 -> BarcodeFormat.EAN_13
        BarcodeType.EAN_8 -> BarcodeFormat.EAN_8
        BarcodeType.UPC_A -> BarcodeFormat.UPC_A
        BarcodeType.UPC_E -> BarcodeFormat.UPC_E
        BarcodeType.QR_CODE -> BarcodeFormat.QR_CODE
    }

    /** @return the module matrix, or an error message the UI can show. */
    fun encode(type: BarcodeType, value: String): Result<BitMatrix> {
        return when (val check = BarcodeRules.validate(type, value)) {
            is BarcodeCheck.Invalid -> Result.failure(IllegalArgumentException(check.reason))
            is BarcodeCheck.Valid -> runCatching {
                val hints = mutableMapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 0)
                if (type == BarcodeType.QR_CODE) {
                    hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
                    hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
                }
                MultiFormatWriter().encode(check.normalized, format(type), 0, 0, hints)
            }
        }
    }

    /** The text printed under the bars. */
    fun humanReadable(type: BarcodeType, value: String): String =
        (BarcodeRules.validate(type, value) as? BarcodeCheck.Valid)?.normalized ?: value
}
