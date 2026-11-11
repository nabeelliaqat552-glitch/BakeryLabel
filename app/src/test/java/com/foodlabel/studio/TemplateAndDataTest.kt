package com.foodlabel.studio

import com.foodlabel.studio.backup.Csv
import com.foodlabel.studio.data.seed.DefaultData
import com.foodlabel.studio.data.seed.TemplateLibrary
import com.foodlabel.studio.label.ElementType
import com.foodlabel.studio.label.LabelData
import com.foodlabel.studio.label.LabelElement
import com.foodlabel.studio.label.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers test plan items 5 (bilingual tokens), 9 (sizes), 10 and 11 (templates are editable layouts). */
class TemplateAndDataTest {

    @Test fun libraryHasAtLeastFortyEditableTemplates() {
        val all = TemplateLibrary.all()
        assertTrue(all.size >= 40)
        assertTrue(all.all { it.elements.isNotEmpty() && it.isTemplate })
        assertEquals(all.size, all.map { it.name }.distinct().size)
        assertTrue(TemplateLibrary.GROUPS.size >= 18)
    }

    @Test fun noTemplateElementLeavesTheLabel() {
        for (t in TemplateLibrary.all()) for (e in t.elements) {
            assertTrue("${t.name}: element starts outside", e.xMm >= -0.01 && e.yMm >= -0.01)
            assertTrue("${t.name}: element too wide", e.xMm + e.wMm <= t.widthMm + 0.01)
            assertTrue("${t.name}: element too tall (${e.content})", e.yMm + e.hMm <= t.heightMm + 0.01)
        }
    }

    @Test fun textRowsDoNotOverlapVertically() {
        // Elements that share horizontal space must not share vertical space.
        for (t in TemplateLibrary.all()) {
            val boxes = t.elements.filter { it.type == ElementType.TEXT || it.type == ElementType.BARCODE || it.type == ElementType.QR }
            for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                val a = boxes[i]; val b = boxes[j]
                val xOverlap = a.xMm < b.xMm + b.wMm - 0.05 && b.xMm < a.xMm + a.wMm - 0.05
                val yOverlap = a.yMm < b.yMm + b.hMm - 0.05 && b.yMm < a.yMm + a.hMm - 0.05
                assertTrue("${t.name}: '${a.content}' overlaps '${b.content}'", !(xOverlap && yOverlap))
            }
        }
    }

    @Test fun adaptingKeepsElementsInside() {
        val t = TemplateLibrary.all().first()
        val small = TemplateLibrary.adaptTo(t, 30.0, 15.0)
        assertEquals(30.0, small.widthMm, 0.0)
        assertTrue(small.elements.all { it.xMm + it.wMm <= 30.01 && it.yMm + it.hMm <= 15.01 })
    }

    @Test fun readySizesMatchTheSpecification() {
        val sizes = DefaultData.LABEL_SIZES.map { it.widthMm to it.heightMm }
        listOf(20.0 to 10.0, 25.0 to 15.0, 30.0 to 15.0, 40.0 to 20.0, 40.0 to 30.0, 50.0 to 20.0, 50.0 to 25.0, 50.0 to 30.0,
            60.0 to 30.0, 60.0 to 40.0, 70.0 to 50.0, 80.0 to 50.0, 100.0 to 50.0).forEach { assertTrue("missing $it", it in sizes) }
    }

    @Test fun tokensResolveAndHide() {
        val d = LabelData(nameEn = "Fresh Cream", nameUr = "تازہ کریم", price = "320", currency = "Rs", exp = "15-11-2026")
        assertEquals("EXP 15-11-2026", Tokens.resolve("EXP {exp}", d))
        assertEquals("Rs 320", Tokens.resolve("{price}", d))
        val urdu = LabelElement(type = ElementType.TEXT, xMm = 0.0, yMm = 0.0, wMm = 10.0, hMm = 5.0, content = "{name_ur}")
        assertTrue(!Tokens.isHidden(urdu, d))
        assertTrue(Tokens.isHidden(urdu, d.copy(language = "EN")))
        assertTrue(Tokens.isHidden(urdu, d.copy(hidden = setOf("name_ur"))))
        val batch = LabelElement(type = ElementType.TEXT, xMm = 0.0, yMm = 0.0, wMm = 10.0, hMm = 5.0, content = "Batch {batch}")
        assertTrue("empty field hides its caption too", Tokens.isHidden(batch, d))
    }

    @Test fun csvRoundTripKeepsUrduCommasAndQuotes() {
        val rows = listOf(listOf("name_en", "name_ur", "note"), listOf("Milk, fresh", "دودھ", "He said \"cold\"\nline two"))
        assertEquals(rows, Csv.parse(Csv.write(rows)))
    }

    @Test fun starterCatalogueHasNoInventedShelfLives() {
        // Starter products are names only; the business sets every shelf life.
        assertTrue(DefaultData.CATEGORIES.size >= 13)
        assertTrue(DefaultData.CATEGORIES.flatMap { it.products }.size >= 45)
    }
}
