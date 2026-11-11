package com.foodlabel.studio.barcode

/** Barcode symbologies the app can generate. */
enum class BarcodeType(val label: String, val is2D: Boolean = false) {
    CODE_128("Code 128"),
    CODE_39("Code 39"),
    EAN_13("EAN-13"),
    EAN_8("EAN-8"),
    UPC_A("UPC-A"),
    UPC_E("UPC-E"),
    QR_CODE("QR Code", is2D = true);

    companion object {
        fun parse(value: String?): BarcodeType = entries.firstOrNull { it.name == value } ?: CODE_128
    }
}

sealed interface BarcodeCheck {
    /** [normalized] is the value to encode (for example with a computed check digit appended). */
    data class Valid(val normalized: String, val note: String? = null) : BarcodeCheck
    data class Invalid(val reason: String) : BarcodeCheck
}

/** Pure validation rules, no Android dependencies, so they are unit tested on the JVM. */
object BarcodeRules {

    private const val CODE39_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. \$/+%"

    /** GS1 mod-10 check digit for the digits given (without the check digit). */
    fun gs1CheckDigit(digits: String): Int {
        require(digits.all { it.isDigit() })
        var sum = 0
        // Weight 3 applies to the right-most digit of the payload, then alternates.
        digits.reversed().forEachIndexed { i, c ->
            val d = c - '0'
            sum += if (i % 2 == 0) d * 3 else d
        }
        return (10 - sum % 10) % 10
    }

    fun validate(type: BarcodeType, raw: String): BarcodeCheck {
        val value = raw.trim()
        if (value.isEmpty()) return BarcodeCheck.Invalid("Enter a barcode value.")
        return when (type) {
            BarcodeType.EAN_13 -> fixedLengthGs1(value, 13, "EAN-13")
            BarcodeType.EAN_8 -> fixedLengthGs1(value, 8, "EAN-8")
            BarcodeType.UPC_A -> fixedLengthGs1(value, 12, "UPC-A")
            BarcodeType.UPC_E -> validateUpcE(value)
            BarcodeType.CODE_39 -> {
                val up = value.uppercase()
                val bad = up.firstOrNull { it !in CODE39_CHARS }
                if (bad != null) BarcodeCheck.Invalid("Code 39 cannot contain '$bad'. Use A–Z, 0–9 and - . \$ / + % space.")
                else if (up.length > 43) BarcodeCheck.Invalid("Code 39 is limited to 43 characters here.")
                else BarcodeCheck.Valid(up)
            }
            BarcodeType.CODE_128 -> {
                val bad = value.firstOrNull { it.code !in 32..126 }
                if (bad != null) BarcodeCheck.Invalid("Code 128 supports plain English letters, digits and symbols only.")
                else if (value.length > 80) BarcodeCheck.Invalid("Code 128 is limited to 80 characters here.")
                else BarcodeCheck.Valid(value)
            }
            BarcodeType.QR_CODE ->
                if (value.length > 900) BarcodeCheck.Invalid("QR content is too long for a small label (max 900 characters).")
                else BarcodeCheck.Valid(value)
        }
    }

    private fun fixedLengthGs1(value: String, length: Int, name: String): BarcodeCheck {
        if (!value.all { it.isDigit() }) return BarcodeCheck.Invalid("$name must contain digits only.")
        return when (value.length) {
            length - 1 -> {
                val full = value + gs1CheckDigit(value)
                BarcodeCheck.Valid(full, "Check digit ${full.last()} added.")
            }
            length -> {
                val expected = gs1CheckDigit(value.dropLast(1))
                if (expected == value.last() - '0') BarcodeCheck.Valid(value)
                else BarcodeCheck.Invalid("Wrong check digit. The last digit should be $expected.")
            }
            else -> BarcodeCheck.Invalid("$name needs ${length - 1} digits (check digit added for you) or $length digits.")
        }
    }

    /** Expands an 8-digit UPC-E (number system + 6 digits + check) to its 12-digit UPC-A form. */
    fun upcEToUpcA(upcE: String): String {
        require(upcE.length == 8 && upcE.all { it.isDigit() })
        val ns = upcE[0]
        val d = upcE.substring(1, 7)
        val check = upcE[7]
        val body = when (d[5]) {
            '0', '1', '2' -> "${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}"
            '3' -> "${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}"
            '4' -> "${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}"
            else -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${d[5]}"
        }
        return "$ns$body$check"
    }

    private fun validateUpcE(value: String): BarcodeCheck {
        if (!value.all { it.isDigit() }) return BarcodeCheck.Invalid("UPC-E must contain digits only.")
        if (value.length != 7 && value.length != 8)
            return BarcodeCheck.Invalid("UPC-E needs 7 digits (check digit added for you) or 8 digits.")
        if (value[0] != '0' && value[0] != '1') return BarcodeCheck.Invalid("UPC-E must start with 0 or 1.")
        val payload7 = value.take(7)
        val upcABody = upcEToUpcA(payload7 + "0").dropLast(1)
        val check = gs1CheckDigit(upcABody)
        return if (value.length == 7) BarcodeCheck.Valid(payload7 + check, "Check digit $check added.")
        else if (value.last() - '0' == check) BarcodeCheck.Valid(value)
        else BarcodeCheck.Invalid("Wrong check digit. The last digit should be $check.")
    }

    /**
     * Internal in-store code. Uses GS1 prefix 2 (reserved for restricted, in-store circulation),
     * so it never collides with real retail numbers. It is NOT a GS1-allocated identifier.
     * Layout: 2 + business(2 digits) + sequence(9 digits) + check digit = 13 digits.
     */
    fun internalEan13(businessNumber: Long, sequence: Long): String {
        val biz = (businessNumber % 100).toString().padStart(2, '0')
        val seq = (sequence % 1_000_000_000L).toString().padStart(9, '0')
        val body = "2$biz$seq"
        return body + gs1CheckDigit(body)
    }
}
