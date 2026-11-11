package com.foodlabel.studio.data.seed

import com.foodlabel.studio.data.db.LabelSizeEntity
import com.foodlabel.studio.data.db.PrinterProfileEntity

/**
 * Starter content for a new business. Everything here is ordinary, editable data: the user can rename,
 * change or delete any category or product. Shelf lives are left empty on purpose: the app does not
 * suggest food-safety periods; the business sets them.
 */
object DefaultData {

    data class Cat(val name: String, val ur: String, val icon: String, val tint: Long, val products: List<Pair<String, String>>)

    val CATEGORIES = listOf(
        Cat("Cakes", "کیک", "cake", 0xFFF3DDD3, listOf(
            "Chocolate Cake" to "چاکلیٹ کیک", "Pineapple Cake" to "پائن ایپل کیک", "Cupcake" to "کپ کیک",
            "Cream Roll" to "کریم رول", "Muffin" to "مفن", "Donut" to "ڈونٹ")),
        Cat("Biscuits & Cookies", "بسکٹ", "cookie", 0xFFF0E3C8, listOf(
            "Butter Cookies" to "بٹر کوکیز", "Nan Khatai" to "نان خطائی", "Zeera Biscuit" to "زیرہ بسکٹ",
            "Cookies" to "کوکیز", "Biscuits" to "بسکٹ")),
        Cat("Bread & Buns", "ڈبل روٹی", "bread", 0xFFEEDFC6, listOf(
            "Bread" to "ڈبل روٹی", "Bun" to "بن", "Burger Bun" to "برگر بن")),
        Cat("Pastries & Puffs", "پیسٹری", "croissant", 0xFFF2E6CC, listOf(
            "Croissant" to "کروسان", "Patties" to "پیٹیز", "Puff Pastry" to "پف پیسٹری", "Pizza" to "پیزا", "Sandwich" to "سینڈوچ")),
        Cat("Rusk & Toast", "رس", "rusk", 0xFFE9D8BD, listOf("Rusk" to "رس")),
        Cat("Sweets", "مٹھائی", "sweets", 0xFFF1D9DC, listOf(
            "Gulab Jamun" to "گلاب جامن", "Barfi" to "برفی", "Laddu" to "لڈو", "Jalebi" to "جلیبی",
            "Rasgulla" to "رس گلہ", "Milk Cake" to "ملک کیک", "Kheer" to "کھیر", "Gajar Halwa" to "گاجر کا حلوہ", "Cham Cham" to "چم چم")),
        Cat("Dairy", "ڈیری", "milk", 0xFFDFE9EC, listOf(
            "Milk" to "دودھ", "Yogurt" to "دہی", "Butter" to "مکھن", "Ghee" to "گھی", "Cream" to "کریم",
            "Cheese" to "چیز", "Paneer" to "پنیر", "Lassi" to "لسی", "Makhan" to "مکھن")),
        Cat("Fast Food", "فاسٹ فوڈ", "burger", 0xFFF0D9C6, listOf(
            "Burger" to "برگر", "Zinger Burger" to "زنگر برگر", "Pizza" to "پیزا", "Shawarma" to "شوارما",
            "Sandwich" to "سینڈوچ", "Fries" to "فرائز", "Nuggets" to "نگٹس", "Chicken Roll" to "چکن رول",
            "Wings" to "ونگز", "Pasta" to "پاستا", "Hot Dog" to "ہاٹ ڈاگ")),
        Cat("Frozen Food", "فروزن", "frozen", 0xFFDCE6EE, emptyList()),
        Cat("Snacks", "اسنیکس", "snack", 0xFFEFE0C2, emptyList()),
        Cat("Beverages", "مشروبات", "drink", 0xFFE1E8DA, emptyList()),
        Cat("Confectionery", "ٹافیاں", "candy", 0xFFEEDCE6, emptyList()),
        Cat("Meat Products", "گوشت", "meat", 0xFFEDD8D2, emptyList()),
    )

    val LABEL_SIZES: List<LabelSizeEntity> = listOf(
        20.0 to 10.0, 25.0 to 15.0, 30.0 to 15.0, 40.0 to 20.0, 40.0 to 30.0, 50.0 to 20.0, 50.0 to 25.0,
        50.0 to 30.0, 60.0 to 30.0, 60.0 to 40.0, 70.0 to 50.0, 80.0 to 50.0, 100.0 to 50.0,
    ).map { (w, h) ->
        LabelSizeEntity(businessId = null, name = "${w.toInt()} × ${h.toInt()} mm", widthMm = w, heightMm = h, gapMm = 2.0)
    } + listOf(
        LabelSizeEntity(businessId = null, name = "Round 40 mm", widthMm = 40.0, heightMm = 40.0, shape = "ROUND"),
        LabelSizeEntity(businessId = null, name = "58 mm roll (continuous)", widthMm = 48.0, heightMm = 30.0, gapMm = 3.0, paperWidthMm = 58.0, continuous = true),
    )

    /**
     * Generic starting points, not claims of tested compatibility with any specific model.
     * Users can add their own profiles for other printers.
     */
    val PRINTER_PROFILES = listOf(
        PrinterProfileEntity(name = "Generic TSPL label printer, 203 DPI", protocol = "TSPL", dpi = 203, headWidthMm = 104.0, builtIn = true,
            notes = "Typical of TSC-compatible 2- and 4-inch label printers. Uses gap sensing."),
        PrinterProfileEntity(name = "Generic TSPL label printer, 300 DPI", protocol = "TSPL", dpi = 300, headWidthMm = 104.0, builtIn = true),
        PrinterProfileEntity(name = "Generic ESC/POS 58 mm", protocol = "ESC_POS", dpi = 203, headWidthMm = 48.0, supportsGap = false, builtIn = true,
            notes = "Receipt-style printers. Labels print as images; set the gap feed to match your roll."),
        PrinterProfileEntity(name = "Generic ESC/POS 80 mm", protocol = "ESC_POS", dpi = 203, headWidthMm = 72.0, supportsGap = false, builtIn = true),
        PrinterProfileEntity(name = "Generic ZPL, 203 DPI", protocol = "ZPL", dpi = 203, headWidthMm = 104.0, builtIn = true),
        PrinterProfileEntity(name = "Generic ZPL, 300 DPI", protocol = "ZPL", dpi = 300, headWidthMm = 104.0, builtIn = true),
        PrinterProfileEntity(name = "Generic CPCL mobile, 203 DPI", protocol = "CPCL", dpi = 203, headWidthMm = 72.0, builtIn = true),
    )
}
