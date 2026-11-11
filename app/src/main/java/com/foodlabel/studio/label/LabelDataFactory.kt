package com.foodlabel.studio.label

import com.foodlabel.studio.barcode.BarcodeType
import com.foodlabel.studio.data.db.BusinessEntity
import com.foodlabel.studio.data.db.ProductBarcodeEntity
import com.foodlabel.studio.data.db.ProductEntity
import com.foodlabel.studio.dates.DateEngine
import com.foodlabel.studio.dates.DateFormatOption
import com.foodlabel.studio.dates.ResolvedDates
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object LabelDataFactory {

    private val priceFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.ENGLISH))

    fun formatPrice(p: Double?): String = p?.let { priceFormat.format(it) } ?: ""

    fun parseCustom(json: String): Map<String, String> = runCatching {
        (Json.parseToJsonElement(json) as JsonObject).mapValues { it.value.jsonPrimitive.content }
    }.getOrDefault(emptyMap())

    fun encodeCustom(map: Map<String, String>): String =
        JsonObject(map.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }).toString()

    fun hiddenSet(s: String): Set<String> = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun build(
        business: BusinessEntity,
        product: ProductEntity,
        barcode: ProductBarcodeEntity?,
        dates: ResolvedDates,
        batchOverride: String? = null,
        language: String = business.labelLanguage,
    ): LabelData {
        val fmt = DateFormatOption.parse(business.dateFormat)
        return LabelData(
            businessName = business.name.uppercase(Locale.ENGLISH),
            businessNameUr = business.nameUrdu,
            nameEn = product.nameEn,
            nameUr = product.nameUr,
            price = formatPrice(product.price),
            currency = business.currencySymbol,
            weight = product.weight,
            mfg = DateEngine.format(dates.manufactured, fmt) +
                if (business.showMfgTime && dates.manufacturedTime != null) " " + DateEngine.formatTime(dates.manufacturedTime) else "",
            mfgTime = DateEngine.formatTime(dates.manufacturedTime),
            exp = DateEngine.format(dates.expiry, fmt),
            batch = batchOverride ?: product.batchNumber,
            code = product.code,
            barcode = barcode?.value.orEmpty(),
            barcodeType = BarcodeType.parse(barcode?.type),
            logoPath = business.logoPath,
            custom = parseCustom(product.customFields),
            hidden = hiddenSet(product.hiddenFields),
            language = language,
        )
    }
}
