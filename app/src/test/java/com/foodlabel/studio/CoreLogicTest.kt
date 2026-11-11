package com.foodlabel.studio

import com.foodlabel.studio.barcode.BarcodeCheck
import com.foodlabel.studio.barcode.BarcodeRules
import com.foodlabel.studio.barcode.BarcodeType
import com.foodlabel.studio.core.Units
import com.foodlabel.studio.dates.DateEngine
import com.foodlabel.studio.dates.ShelfLifeUnit
import com.foodlabel.studio.printing.driver.CpclDriver
import com.foodlabel.studio.printing.driver.EscPosDriver
import com.foodlabel.studio.printing.driver.MonoBitmap
import com.foodlabel.studio.printing.driver.RasterJob
import com.foodlabel.studio.printing.driver.TsplDriver
import com.foodlabel.studio.printing.driver.ZplDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Covers test plan items 6, 7, 8 (validation part) and 13 (dimension maths). */
class CoreLogicTest {

    @Test fun expiryFreshCreamExample() {
        val exp = DateEngine.calculateExpiry(LocalDate.of(2026, 11, 10), 5, ShelfLifeUnit.DAYS)
        assertEquals(LocalDate.of(2026, 11, 15), exp)
    }

    @Test fun expiryCrossesMonthAndYear() {
        assertEquals(LocalDate.of(2027, 1, 4), DateEngine.calculateExpiry(LocalDate.of(2026, 12, 28), 7, ShelfLifeUnit.DAYS))
        assertEquals(LocalDate.of(2026, 12, 1), DateEngine.calculateExpiry(LocalDate.of(2026, 11, 10), 3, ShelfLifeUnit.WEEKS))
    }

    @Test fun expiryMonthsClampToMonthEnd() {
        assertEquals(LocalDate.of(2027, 2, 28), DateEngine.calculateExpiry(LocalDate.of(2027, 1, 31), 1, ShelfLifeUnit.MONTHS))
        assertEquals(LocalDate.of(2028, 2, 29), DateEngine.calculateExpiry(LocalDate.of(2028, 1, 31), 1, ShelfLifeUnit.MONTHS))
    }

    @Test fun leapDay() {
        assertEquals(LocalDate.of(2028, 3, 1), DateEngine.calculateExpiry(LocalDate.of(2028, 2, 28), 2, ShelfLifeUnit.DAYS))
    }

    @Test fun manualExpiryIsNeverRecalculated() {
        val manual = LocalDate.of(2026, 12, 25)
        val r = DateEngine.resolve(LocalDate.of(2026, 11, 10), null, 5, ShelfLifeUnit.DAYS, manual)
        assertEquals(manual, r.expiry)
        assertTrue(r.expiryIsManual)
    }

    @Test fun noShelfLifeMeansNoInventedExpiry() {
        val r = DateEngine.resolve(LocalDate.of(2026, 11, 10), null, null, ShelfLifeUnit.DAYS, null)
        assertEquals(null, r.expiry)
    }

    @Test fun gs1CheckDigits() {
        assertEquals(1, BarcodeRules.gs1CheckDigit("400638133393"))
        val ok = BarcodeRules.validate(BarcodeType.EAN_13, "4006381333931")
        assertTrue(ok is BarcodeCheck.Valid)
        val bad = BarcodeRules.validate(BarcodeType.EAN_13, "4006381333932")
        assertTrue(bad is BarcodeCheck.Invalid)
        val added = BarcodeRules.validate(BarcodeType.EAN_8, "9638507") as BarcodeCheck.Valid
        assertEquals("96385074", added.normalized)
        val upcA = BarcodeRules.validate(BarcodeType.UPC_A, "03600029145") as BarcodeCheck.Valid
        assertEquals("036000291452", upcA.normalized)
    }

    @Test fun upcE() {
        val v = BarcodeRules.validate(BarcodeType.UPC_E, "0425261") as BarcodeCheck.Valid
        assertEquals("04252614", v.normalized)
        assertEquals("042100005264", BarcodeRules.upcEToUpcA("04252614"))
    }

    @Test fun internalCodesUseInStorePrefix() {
        val code = BarcodeRules.internalEan13(3, 14)
        assertEquals(13, code.length)
        assertTrue(code.startsWith("203"))
        assertTrue(BarcodeRules.validate(BarcodeType.EAN_13, code) is BarcodeCheck.Valid)
    }

    @Test fun code39RejectsLowercaseSymbols() {
        assertTrue(BarcodeRules.validate(BarcodeType.CODE_39, "abc-1") is BarcodeCheck.Valid)
        assertTrue(BarcodeRules.validate(BarcodeType.CODE_39, "AB#1") is BarcodeCheck.Invalid)
    }

    @Test fun physicalSizeToDots() {
        assertEquals(400, Units.mmToDots(50.0, 203))
        assertEquals(240, Units.mmToDots(30.0, 203))
        assertEquals(591, Units.mmToDots(50.0, 300))
    }

    private fun sampleJob(): RasterJob {
        val img = MonoBitmap(16, 2)
        img.set(0, 0, true); img.set(15, 1, true)
        return RasterJob(img, 203, 2.0, 0.25, 2.0, copies = 3)
    }

    @Test fun tsplInvertsBitsAndPrintsCopies() {
        val s = String(TsplDriver.build(sampleJob()), Charsets.ISO_8859_1)
        assertTrue(s.startsWith("SIZE 2 mm,0.2 mm") || s.startsWith("SIZE 2 mm,0.3 mm"))
        assertTrue(s.contains("BITMAP 0,0,2,2,0,"))
        assertTrue(s.contains("PRINT 3,1"))
        val start = s.indexOf("BITMAP 0,0,2,2,0,") + "BITMAP 0,0,2,2,0,".length
        assertEquals(0x7F, s[start].code) // first byte 0x80 inverted
    }

    @Test fun zplGraphicField() {
        val s = String(ZplDriver.build(sampleJob()), Charsets.US_ASCII)
        assertTrue(s.contains("^GFA,4,4,2,80000001^FS"))
        assertTrue(s.contains("^PQ3"))
    }

    @Test fun cpclAndEscPos() {
        val c = String(CpclDriver.build(sampleJob()), Charsets.US_ASCII)
        assertTrue(c.startsWith("! 0 203 203 2 3"))
        assertTrue(c.contains("EG 2 2 0 0 80000001"))
        val e = EscPosDriver.build(sampleJob())
        // three copies -> three GS v 0 headers
        var count = 0
        for (i in 0 until e.size - 3) if (e[i] == 0x1D.toByte() && e[i + 1] == 0x76.toByte() && e[i + 2] == 0x30.toByte()) count++
        assertEquals(3, count)
    }
}
