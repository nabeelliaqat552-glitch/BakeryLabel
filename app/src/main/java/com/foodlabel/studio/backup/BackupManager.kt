package com.foodlabel.studio.backup

import android.content.Context
import com.foodlabel.studio.barcode.BarcodeCheck
import com.foodlabel.studio.barcode.BarcodeRules
import com.foodlabel.studio.barcode.BarcodeType
import com.foodlabel.studio.data.db.CategoryEntity
import com.foodlabel.studio.data.db.ProductEntity
import com.foodlabel.studio.data.repo.BusinessBundle
import com.foodlabel.studio.data.repo.Repository
import com.foodlabel.studio.dates.ShelfLifeUnit
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportReport(val added: Int, val skipped: Int, val messages: List<String>)

/** What a backup file contains, read before anything is written, so the user can confirm. */
data class BackupContents(val bundles: List<BusinessBundle>, val logos: Map<String, ByteArray>, val products: Int)

/**
 * Backups are ordinary ZIP files the user saves wherever they like (phone storage, USB drive, a cloud
 * app they choose). Nothing is uploaded by this app.
 */
class BackupManager(private val context: Context, private val repo: Repository) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun exportZip(businessIds: List<Long>, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"app":"FoodLabel Studio","format":1,"createdAt":${System.currentTimeMillis()},"businesses":${businessIds.size}}""".toByteArray())
            zip.closeEntry()
            for (id in businessIds) {
                val bundle = repo.exportBundle(id)
                zip.putNextEntry(ZipEntry("businesses/$id.json"))
                zip.write(json.encodeToString(bundle).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                bundle.business.logoPath?.let { path ->
                    val f = File(path)
                    if (f.exists()) {
                        zip.putNextEntry(ZipEntry("logos/$id.${f.extension.ifBlank { "png" }}"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                zip.putNextEntry(ZipEntry("products/$id.csv"))
                zip.write(productsCsv(id).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    suspend fun exportAllZip(out: OutputStream) =
        exportZip(repo.businesses(includeArchived = true).first().map { it.id }, out)

    suspend fun exportJson(businessId: Long, out: OutputStream) {
        out.write(json.encodeToString(repo.exportBundle(businessId)).toByteArray(Charsets.UTF_8))
    }

    /** Reads a ZIP or a single JSON backup without changing any data. */
    fun read(input: InputStream): BackupContents {
        val bytes = input.readBytes()
        if (bytes.isNotEmpty() && bytes[0] == '{'.code.toByte()) {
            val b = json.decodeFromString<BusinessBundle>(bytes.toString(Charsets.UTF_8))
            return BackupContents(listOf(b), emptyMap(), b.products.size)
        }
        val bundles = mutableListOf<BusinessBundle>()
        val logos = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                if (name.contains("..")) continue
                when {
                    name.startsWith("businesses/") && name.endsWith(".json") ->
                        bundles += json.decodeFromString<BusinessBundle>(zip.readBytes().toString(Charsets.UTF_8))
                    name.startsWith("logos/") -> logos[name.substringAfter("logos/").substringBefore('.')] = zip.readBytes()
                }
            }
        }
        if (bundles.isEmpty()) throw IllegalArgumentException("This file is not a FoodLabel Studio backup.")
        return BackupContents(bundles, logos, bundles.sumOf { it.products.size })
    }

    /**
     * Restores businesses. With [replaceSameName] a current business with the same name is deleted first;
     * the UI must confirm that with the user. Otherwise each business is added as a new one.
     */
    suspend fun restore(contents: BackupContents, replaceSameName: Boolean): List<Long> {
        val current = repo.businesses(includeArchived = true).first()
        return contents.bundles.map { bundle ->
            if (replaceSameName) current.filter { it.name.equals(bundle.business.name, ignoreCase = true) }.forEach { repo.deleteBusiness(it.id) }
            val logoBytes = contents.logos[bundle.business.id.toString()]
            val logoPath = logoBytes?.let { saveLogo(it) }
            val newId = repo.importBundle(bundle.copy(business = bundle.business.copy(logoPath = logoPath)))
            newId
        }.also { ids -> ids.firstOrNull()?.let { repo.setActive(it) } }
    }

    fun saveLogo(bytes: ByteArray): String {
        val dir = File(context.filesDir, "logos").apply { mkdirs() }
        val f = File(dir, "logo_${System.currentTimeMillis()}.png")
        f.writeBytes(bytes)
        return f.absolutePath
    }

    // ---------------- CSV ----------------

    private val header = listOf("name_en", "name_ur", "category", "code", "price", "weight", "shelf_life", "shelf_unit",
        "batch", "barcode_type", "barcode", "favourite")

    suspend fun productsCsv(businessId: Long): String {
        val cats = repo.db.categories().all(businessId).associateBy { it.id }
        val rows = mutableListOf(header)
        repo.db.products().all(businessId).forEach { p ->
            val bc = repo.barcodeFor(businessId, p.id)
            rows += listOf(p.nameEn, p.nameUr, p.categoryId?.let { cats[it]?.name }.orEmpty(), p.code,
                p.price?.toString().orEmpty(), p.weight, p.shelfLife?.toString().orEmpty(), p.shelfUnit, p.batchNumber,
                bc?.type.orEmpty(), bc?.value.orEmpty(), if (p.favourite) "yes" else "")
        }
        return Csv.write(rows)
    }

    suspend fun importCsv(businessId: Long, input: InputStream): ImportReport {
        val rows = Csv.parse(input.readBytes().toString(Charsets.UTF_8))
        if (rows.isEmpty()) return ImportReport(0, 0, listOf("The file is empty."))
        val head = rows.first().map { it.trim().lowercase() }
        val idx = { name: String -> head.indexOf(name) }
        if (idx("name_en") < 0) return ImportReport(0, rows.size, listOf("The first row must contain the column name_en. Export a CSV from the app to see the format."))
        val cats = repo.db.categories().all(businessId).associateBy { it.name.lowercase() }.toMutableMap()
        var added = 0; var skipped = 0
        val messages = mutableListOf<String>()
        rows.drop(1).forEachIndexed { n, r ->
            val line = n + 2
            fun col(name: String) = idx(name).let { if (it >= 0 && it < r.size) r[it].trim() else "" }
            val nameEn = col("name_en")
            if (nameEn.isBlank()) { skipped++; messages += "Row $line: no English name, skipped."; return@forEachIndexed }
            val catName = col("category")
            val catId = if (catName.isBlank()) null else cats[catName.lowercase()]?.id ?: run {
                val id = repo.saveCategory(CategoryEntity(businessId = businessId, name = catName))
                cats[catName.lowercase()] = CategoryEntity(id = id, businessId = businessId, name = catName); id
            }
            val type = col("barcode_type").takeIf { it.isNotBlank() }?.let { BarcodeType.parse(it.uppercase().replace('-', '_')) }
            val rawCode = col("barcode")
            var codeValue: String? = null
            if (rawCode.isNotBlank()) {
                when (val chk = BarcodeRules.validate(type ?: BarcodeType.CODE_128, rawCode)) {
                    is BarcodeCheck.Valid -> codeValue = chk.normalized
                    is BarcodeCheck.Invalid -> messages += "Row $line ($nameEn): barcode not imported. ${chk.reason}"
                }
            }
            val price = col("price").replace(",", "").toDoubleOrNull()
            repo.saveProduct(
                ProductEntity(businessId = businessId, categoryId = catId, nameEn = nameEn, nameUr = col("name_ur"),
                    code = col("code"), price = price, weight = col("weight"), shelfLife = col("shelf_life").toIntOrNull(),
                    shelfUnit = ShelfLifeUnit.parse(col("shelf_unit")).name, batchNumber = col("batch"),
                    favourite = col("favourite").lowercase() in setOf("yes", "true", "1")),
                if (codeValue != null) (type ?: BarcodeType.CODE_128) else null, codeValue, internal = false,
            )
            added++
        }
        return ImportReport(added, skipped, messages)
    }
}
