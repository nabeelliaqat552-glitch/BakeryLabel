package com.foodlabel.studio.label

import com.foodlabel.studio.barcode.BarcodeType
import com.foodlabel.studio.data.db.LabelDesignEntity
import com.foodlabel.studio.data.db.LabelElementEntity
import java.util.concurrent.atomic.AtomicLong

enum class ElementType(val label: String) {
    TEXT("Text"), BARCODE("Barcode"), QR("QR code"), IMAGE("Logo / image"), LINE("Line"), BOX("Box");

    companion object { fun parse(v: String) = entries.firstOrNull { it.name == v } ?: TEXT }
}

enum class TextAlignOption { START, CENTER, END; companion object { fun parse(v: String) = entries.firstOrNull { it.name == v } ?: START } }

enum class FontOption(val label: String) {
    SANS("Sans"), SERIF("Serif"), CONDENSED("Condensed"), MONO("Mono");

    companion object { fun parse(v: String) = entries.firstOrNull { it.name == v } ?: SANS }
}

/** One editable item on a label. Geometry is in millimetres from the label's top-left corner. */
data class LabelElement(
    val uid: Long = nextUid(),
    val type: ElementType,
    val xMm: Double,
    val yMm: Double,
    val wMm: Double,
    val hMm: Double,
    val rotation: Int = 0,
    val z: Int = 0,
    val content: String = "",
    val fontPt: Double = 8.0,
    val font: FontOption = FontOption.SANS,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val align: TextAlignOption = TextAlignOption.START,
    val visible: Boolean = true,
    val inverted: Boolean = false,
    val strokeMm: Double = 0.3,
    val barcodeType: BarcodeType = BarcodeType.CODE_128,
    val humanReadable: Boolean = true,
    val imagePath: String? = null,
    val maxLines: Int = 2,
) {
    val displayName: String
        get() = when (type) {
            ElementType.TEXT -> Tokens.describe(content)
            ElementType.BARCODE -> "Barcode · ${barcodeType.label}"
            else -> type.label
        }

    companion object {
        private val counter = AtomicLong(System.nanoTime())
        fun nextUid() = counter.incrementAndGet()
    }
}

data class LabelDesign(
    val id: Long = 0,
    val businessId: Long? = null,
    val name: String,
    val groupName: String = "Custom",
    val isTemplate: Boolean = false,
    val widthMm: Double,
    val heightMm: Double,
    val round: Boolean = false,
    val borderMm: Double = 0.0,
    val cornerMm: Double = 0.0,
    val insetMm: Double = 0.0,
    val backgroundBlack: Boolean = false,
    val builtInKey: String? = null,
    val elements: List<LabelElement> = emptyList(),
) {
    fun toEntity(): LabelDesignEntity = LabelDesignEntity(
        id = id, businessId = businessId, name = name, groupName = groupName, isTemplate = isTemplate,
        widthMm = widthMm, heightMm = heightMm, shape = if (round) "ROUND" else "RECT",
        borderMm = borderMm, cornerMm = cornerMm, insetMm = insetMm, backgroundBlack = backgroundBlack,
        builtInKey = builtInKey, updatedAt = System.currentTimeMillis(),
    )

    fun elementEntities(): List<LabelElementEntity> = elements.map {
        LabelElementEntity(
            designId = id, type = it.type.name, xMm = it.xMm, yMm = it.yMm, wMm = it.wMm, hMm = it.hMm,
            rotation = it.rotation, z = it.z, content = it.content, fontPt = it.fontPt, fontFamily = it.font.name,
            bold = it.bold, italic = it.italic, align = it.align.name, visible = it.visible, inverted = it.inverted,
            strokeMm = it.strokeMm, barcodeType = it.barcodeType.name, humanReadable = it.humanReadable,
            imagePath = it.imagePath, maxLines = it.maxLines,
        )
    }

    companion object {
        fun from(d: LabelDesignEntity, els: List<LabelElementEntity>) = LabelDesign(
            id = d.id, businessId = d.businessId, name = d.name, groupName = d.groupName, isTemplate = d.isTemplate,
            widthMm = d.widthMm, heightMm = d.heightMm, round = d.shape == "ROUND", borderMm = d.borderMm,
            cornerMm = d.cornerMm, insetMm = d.insetMm, backgroundBlack = d.backgroundBlack, builtInKey = d.builtInKey,
            elements = els.sortedBy { it.z }.map {
                LabelElement(
                    type = ElementType.parse(it.type), xMm = it.xMm, yMm = it.yMm, wMm = it.wMm, hMm = it.hMm,
                    rotation = it.rotation, z = it.z, content = it.content, fontPt = it.fontPt,
                    font = FontOption.parse(it.fontFamily), bold = it.bold, italic = it.italic,
                    align = TextAlignOption.parse(it.align), visible = it.visible, inverted = it.inverted,
                    strokeMm = it.strokeMm, barcodeType = BarcodeType.parse(it.barcodeType),
                    humanReadable = it.humanReadable, imagePath = it.imagePath, maxLines = it.maxLines,
                )
            },
        )
    }
}

/** Values filled into a label for one print. */
data class LabelData(
    val businessName: String = "",
    val businessNameUr: String = "",
    val nameEn: String = "",
    val nameUr: String = "",
    val price: String = "",
    val currency: String = "",
    val weight: String = "",
    val mfg: String = "",
    val mfgTime: String = "",
    val exp: String = "",
    val batch: String = "",
    val code: String = "",
    val barcode: String = "",
    val barcodeType: BarcodeType = BarcodeType.CODE_128,
    val logoPath: String? = null,
    val custom: Map<String, String> = emptyMap(),
    /** Field keys the user switched off for this product, see [Tokens.FIELDS]. */
    val hidden: Set<String> = emptySet(),
    /** EN, UR or BOTH: hides the other language's name tokens. */
    val language: String = "BOTH",
)

/** Placeholders a text element can contain, for example "EXP {exp}". */
object Tokens {
    data class Field(val key: String, val token: String, val label: String)

    val FIELDS = listOf(
        Field("business", "{business}", "Business name"),
        Field("business_ur", "{business_ur}", "Business name (Urdu)"),
        Field("name_en", "{name_en}", "Product name (English)"),
        Field("name_ur", "{name_ur}", "Product name (Urdu)"),
        Field("price", "{price}", "Price"),
        Field("weight", "{weight}", "Weight"),
        Field("mfg", "{mfg}", "Manufacturing date"),
        Field("mfg_time", "{mfg_time}", "Manufacturing time"),
        Field("exp", "{exp}", "Expiry date"),
        Field("batch", "{batch}", "Batch number"),
        Field("code", "{code}", "Product code"),
        Field("barcode", "{barcode}", "Barcode number"),
    )

    /** Fields a user can switch off per product (Show on label). */
    val TOGGLEABLE = listOf("name_ur", "mfg", "exp", "price", "weight", "batch", "barcode", "code", "business", "logo")

    private val regex = Regex("\\{([a-z_]+)(?::([^}]+))?\\}")

    fun keysIn(text: String): Set<String> = regex.findAll(text).map { it.groupValues[1] }.toSet()

    /** Field key that decides visibility for a whole element. */
    fun fieldKeysFor(e: LabelElement): Set<String> = when (e.type) {
        ElementType.BARCODE -> setOf("barcode") + keysIn(e.content)
        ElementType.IMAGE -> if (e.imagePath == null) setOf("logo") else emptySet()
        else -> keysIn(e.content).map { if (it == "price") "price" else it }.toSet()
    }

    fun resolve(text: String, d: LabelData): String = regex.replace(text) { m ->
        val key = m.groupValues[1]
        val arg = m.groupValues[2]
        when (key) {
            "business" -> d.businessName
            "business_ur" -> d.businessNameUr
            "name_en" -> d.nameEn
            "name_ur" -> d.nameUr
            "price" -> if (d.price.isBlank()) "" else listOf(d.currency, d.price).filter { it.isNotBlank() }.joinToString(" ")
            "weight" -> d.weight
            "mfg" -> d.mfg
            "mfg_time" -> d.mfgTime
            "exp" -> d.exp
            "batch" -> d.batch
            "code" -> d.code
            "barcode" -> d.barcode
            "custom" -> d.custom[arg].orEmpty()
            else -> m.value
        }
    }

    /** True when the element should be left off this label. */
    fun isHidden(e: LabelElement, d: LabelData): Boolean {
        if (!e.visible) return true
        val keys = fieldKeysFor(e)
        if (keys.any { it in d.hidden }) return true
        if (d.language == "EN" && ("name_ur" in keys || "business_ur" in keys)) return true
        if (d.language == "UR" && "name_en" in keys) return true
        if (e.type == ElementType.TEXT && keys.isNotEmpty()) {
            // A text like "EXP {exp}" is dropped when every token inside it is empty.
            val allEmpty = regex.findAll(e.content).all { resolve(it.value, d).isBlank() }
            if (allEmpty) return true
        }
        if (e.type == ElementType.BARCODE && resolve(e.content.ifBlank { "{barcode}" }, d).isBlank()) return true
        if (e.type == ElementType.QR && resolve(e.content, d).isBlank()) return true
        if (e.type == ElementType.IMAGE && (e.imagePath ?: d.logoPath) == null) return true
        return false
    }

    fun describe(text: String): String {
        val f = FIELDS.firstOrNull { text.contains(it.token) }
        return when {
            f != null && text.trim() == f.token -> f.label
            f != null -> "${f.label} · \"${text.take(18)}\""
            else -> "Text · \"${text.take(18)}\""
        }
    }

    val SAMPLE = LabelData(
        businessName = "ABC BAKERY", businessNameUr = "اے بی سی بیکری", nameEn = "Fresh Cream", nameUr = "تازہ کریم",
        price = "320", currency = "Rs", weight = "200 g", mfg = "10-11-2026", mfgTime = "09:30", exp = "15-11-2026",
        batch = "B-1110", code = "DY-014", barcode = com.foodlabel.studio.barcode.BarcodeRules.internalEan13(1, 14), barcodeType = BarcodeType.EAN_13,
    )
}
