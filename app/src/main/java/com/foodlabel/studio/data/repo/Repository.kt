package com.foodlabel.studio.data.repo

import androidx.room.withTransaction
import com.foodlabel.studio.barcode.BarcodeRules
import com.foodlabel.studio.barcode.BarcodeType
import com.foodlabel.studio.data.db.AppDatabase
import com.foodlabel.studio.data.db.AppSettingsEntity
import com.foodlabel.studio.data.db.BusinessEntity
import com.foodlabel.studio.data.db.CategoryEntity
import com.foodlabel.studio.data.db.LabelDesignEntity
import com.foodlabel.studio.data.db.LabelElementEntity
import com.foodlabel.studio.data.db.LabelSizeEntity
import com.foodlabel.studio.data.db.PrintHistoryEntity
import com.foodlabel.studio.data.db.PrintJobEntity
import com.foodlabel.studio.data.db.PrinterEntity
import com.foodlabel.studio.data.db.ProductBarcodeEntity
import com.foodlabel.studio.data.db.ProductEntity
import com.foodlabel.studio.data.db.ShelfLifeEntity
import com.foodlabel.studio.data.seed.DefaultData
import com.foodlabel.studio.data.seed.TemplateLibrary
import com.foodlabel.studio.label.LabelDesign
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** Everything that belongs to one business, used for backup, restore and "Duplicate business". */
@Serializable
data class BusinessBundle(
    val formatVersion: Int = 1,
    val app: String = "FoodLabel Studio",
    val business: BusinessEntity,
    val categories: List<CategoryEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val barcodes: List<ProductBarcodeEntity> = emptyList(),
    val shelfRules: List<ShelfLifeEntity> = emptyList(),
    val labelSizes: List<LabelSizeEntity> = emptyList(),
    val designs: List<DesignBundle> = emptyList(),
    val printers: List<PrinterEntity> = emptyList(),
    val jobs: List<PrintJobEntity> = emptyList(),
    val history: List<PrintHistoryEntity> = emptyList(),
)

@Serializable
data class DesignBundle(val design: LabelDesignEntity, val elements: List<LabelElementEntity>)

data class ProductWithBarcode(val product: ProductEntity, val barcode: ProductBarcodeEntity?)

@OptIn(ExperimentalCoroutinesApi::class)
class Repository(val db: AppDatabase) {

    // ---------- active business ----------

    val activeBusinessId: Flow<Long?> =
        db.settings().observe(KEY_ACTIVE).map { it?.toLongOrNull() }.distinctUntilChanged()

    val activeBusiness: Flow<BusinessEntity?> =
        activeBusinessId.flatMapLatest { id -> if (id == null) flowOf(null) else db.businesses().observe(id) }

    suspend fun activeId(): Long? = db.settings().get(KEY_ACTIVE)?.toLongOrNull()

    suspend fun setActive(id: Long) = db.settings().put(AppSettingsEntity(KEY_ACTIVE, id.toString()))

    suspend fun getSetting(key: String): String? = db.settings().get(key)
    fun observeSetting(key: String): Flow<String?> = db.settings().observe(key)
    suspend fun putSetting(key: String, value: String) = db.settings().put(AppSettingsEntity(key, value))

    /** Built-in sizes, templates and printer profiles, inserted once. */
    suspend fun ensureBuiltIns() {
        db.withTransaction {
            if (db.labelSizes().builtInCount() == 0) db.labelSizes().insertAll(DefaultData.LABEL_SIZES)
            if (db.printerProfiles().builtInCount() == 0) db.printerProfiles().insertAll(DefaultData.PRINTER_PROFILES)
            if (db.designs().builtInCount() == 0) {
                TemplateLibrary.all().forEach { d -> db.designs().save(d.toEntity(), d.elementEntities()) }
            }
        }
        db.printJobs().failInterrupted()
    }

    // ---------- businesses ----------

    fun businesses(includeArchived: Boolean): Flow<List<BusinessEntity>> =
        if (includeArchived) db.businesses().observeAll() else db.businesses().observeActive()

    suspend fun getBusiness(id: Long) = db.businesses().get(id)

    suspend fun createBusiness(b: BusinessEntity, withStarterProducts: Boolean): Long = db.withTransaction {
        val id = db.businesses().insert(b.copy(id = 0))
        DefaultData.CATEGORIES.forEachIndexed { i, c ->
            val catId = db.categories().insert(
                CategoryEntity(businessId = id, name = c.name, nameUrdu = c.ur, iconKey = c.icon, tint = c.tint, sortOrder = i)
            )
            if (withStarterProducts) c.products.forEach { (en, ur) ->
                db.products().insert(ProductEntity(businessId = id, categoryId = catId, nameEn = en, nameUr = ur))
            }
        }
        val size = db.labelSizes().all(id).firstOrNull { it.widthMm == 50.0 && it.heightMm == 30.0 }
        if (size != null) db.businesses().update(db.businesses().get(id)!!.copy(defaultLabelSizeId = size.id))
        setActive(id)
        id
    }

    suspend fun updateBusiness(b: BusinessEntity) = db.businesses().update(b)

    suspend fun setArchived(id: Long, archived: Boolean) {
        val b = db.businesses().get(id) ?: return
        db.businesses().update(b.copy(archived = archived))
        if (archived && activeId() == id) {
            db.businesses().all().firstOrNull { !it.archived && it.id != id }?.let { setActive(it.id) }
                ?: db.settings().put(AppSettingsEntity(KEY_ACTIVE, ""))
        }
    }

    suspend fun deleteBusiness(id: Long) {
        val b = db.businesses().get(id) ?: return
        db.businesses().delete(b)
        if (activeId() == id) {
            db.businesses().all().firstOrNull { !it.archived }?.let { setActive(it.id) }
                ?: db.settings().put(AppSettingsEntity(KEY_ACTIVE, ""))
        }
    }

    suspend fun duplicateBusiness(id: Long, newName: String): Long {
        val bundle = exportBundle(id)
        return importBundle(bundle.copy(business = bundle.business.copy(name = newName), jobs = emptyList(), history = emptyList()))
    }

    // ---------- categories ----------

    fun categories(businessId: Long) = db.categories().observe(businessId)
    fun categoriesWithCounts(businessId: Long) = db.categories().observeWithCounts(businessId)
    suspend fun getCategory(businessId: Long, id: Long) = db.categories().get(businessId, id)

    suspend fun saveCategory(c: CategoryEntity): Long =
        if (c.id == 0L) db.categories().insert(c.copy(sortOrder = db.categories().maxSort(c.businessId) + 1))
        else { db.categories().update(c); c.id }

    suspend fun deleteCategory(c: CategoryEntity) = db.categories().delete(c)

    // ---------- products ----------

    fun searchProducts(businessId: Long, q: String, categoryId: Long?, favouritesOnly: Boolean) =
        db.products().search(businessId, q.trim(), categoryId, favouritesOnly)

    fun productCount(businessId: Long) = db.products().observeCount(businessId)

    suspend fun getProduct(businessId: Long, id: Long): ProductWithBarcode? {
        val p = db.products().get(businessId, id) ?: return null
        return ProductWithBarcode(p, db.barcodes().forProduct(businessId, id).firstOrNull())
    }

    suspend fun barcodeFor(businessId: Long, productId: Long) = db.barcodes().forProduct(businessId, productId).firstOrNull()

    suspend fun isBarcodeUsedElsewhere(businessId: Long, value: String, productId: Long) =
        db.barcodes().countDuplicates(businessId, value, productId) > 0

    /** Saves a product and its primary barcode (null removes the barcode). */
    suspend fun saveProduct(p: ProductEntity, barcodeType: BarcodeType?, barcodeValue: String?, internal: Boolean): Long =
        db.withTransaction {
            val now = System.currentTimeMillis()
            val id = if (p.id == 0L) db.products().insert(p.copy(createdAt = now, updatedAt = now))
            else { db.products().update(p.copy(updatedAt = now)); p.id }
            db.barcodes().deleteForProduct(p.businessId, id)
            if (barcodeType != null && !barcodeValue.isNullOrBlank()) {
                db.barcodes().insert(ProductBarcodeEntity(productId = id, businessId = p.businessId, type = barcodeType.name, value = barcodeValue, isInternal = internal))
            }
            id
        }

    suspend fun duplicateProduct(businessId: Long, id: Long): Long? {
        val src = getProduct(businessId, id) ?: return null
        // Barcodes must stay unique, so the copy starts without one.
        return saveProduct(src.product.copy(id = 0, nameEn = src.product.nameEn + " (copy)", favourite = false), null, null, false)
    }

    suspend fun deleteProduct(p: ProductEntity) = db.products().delete(p)
    suspend fun setFavourite(businessId: Long, id: Long, fav: Boolean) = db.products().setFavourite(businessId, id, fav)
    suspend fun setProductDesign(businessId: Long, id: Long, designId: Long?) = db.products().setDesign(businessId, id, designId)

    /** Next internal EAN-13 for this business (in-store prefix 2, never a GS1-allocated number). */
    suspend fun nextInternalBarcode(businessId: Long): String = db.withTransaction {
        var code: String
        do {
            db.businesses().bumpSequence(businessId)
            code = BarcodeRules.internalEan13(businessId, db.businesses().sequence(businessId))
        } while (db.barcodes().countDuplicates(businessId, code, -1) > 0)
        code
    }

    // ---------- shelf-life rules ----------

    fun shelfRules(businessId: Long) = db.shelfLife().observe(businessId)
    suspend fun saveShelfRule(r: ShelfLifeEntity) = db.shelfLife().upsert(r)
    suspend fun deleteShelfRule(r: ShelfLifeEntity) = db.shelfLife().delete(r)

    // ---------- label sizes ----------

    fun labelSizes(businessId: Long) = db.labelSizes().observe(businessId)
    suspend fun getLabelSize(id: Long) = db.labelSizes().get(id)
    suspend fun saveLabelSize(s: LabelSizeEntity): Long = if (s.id == 0L) db.labelSizes().insert(s) else { db.labelSizes().update(s); s.id }
    suspend fun deleteLabelSize(s: LabelSizeEntity) { if (s.businessId != null) db.labelSizes().delete(s) }

    // ---------- designs & templates ----------

    fun templates(businessId: Long): Flow<List<LabelDesign>> =
        db.designs().observeTemplates(businessId).map { list -> list.map { LabelDesign.from(it.design, it.elements) } }

    fun savedDesigns(businessId: Long): Flow<List<LabelDesign>> =
        db.designs().observeSaved(businessId).map { list -> list.map { LabelDesign.from(it.design, it.elements) } }

    suspend fun getDesign(id: Long): LabelDesign? = db.designs().get(id)?.let { LabelDesign.from(it.design, it.elements) }

    /** Built-in templates are never overwritten: saving one creates a business copy. */
    suspend fun saveDesign(d: LabelDesign, businessId: Long): Long {
        val owned = if (d.businessId == null || d.businessId != businessId) d.copy(id = 0, businessId = businessId, builtInKey = null) else d
        return db.designs().save(owned.toEntity(), owned.elementEntities())
    }

    suspend fun deleteDesign(d: LabelDesign) {
        if (d.businessId == null) return
        db.designs().get(d.id)?.let { db.designs().deleteDesign(it.design) }
    }

    // ---------- printers ----------

    fun printers(businessId: Long) = db.printers().observe(businessId)
    fun printerProfiles() = db.printerProfiles().observe()
    suspend fun getPrinter(businessId: Long, id: Long) = db.printers().get(businessId, id)
    suspend fun defaultPrinter(businessId: Long) = db.printers().all(businessId).firstOrNull()

    suspend fun savePrinter(p: PrinterEntity): Long {
        val id = if (p.id == 0L) db.printers().insert(p) else { db.printers().update(p); p.id }
        if (p.isDefault || db.printers().all(p.businessId).size == 1) db.printers().makeDefault(p.businessId, id)
        return id
    }

    suspend fun makeDefaultPrinter(businessId: Long, id: Long) = db.printers().makeDefault(businessId, id)
    suspend fun deletePrinter(p: PrinterEntity) = db.printers().delete(p)

    // ---------- print jobs ----------

    fun jobs(businessId: Long) = db.printJobs().observe(businessId)
    fun savedBatches(businessId: Long) = db.printJobs().observeSavedBatches(businessId)
    suspend fun getJob(id: Long) = db.printJobs().get(id)
    suspend fun jobHistory(id: Long) = db.printJobs().historyFor(id)
    suspend fun insertJob(j: PrintJobEntity, lines: List<PrintHistoryEntity>): Long = db.withTransaction {
        val id = db.printJobs().insert(j)
        db.printJobs().insertHistory(lines.map { it.copy(jobId = id) })
        id
    }
    suspend fun updateJob(j: PrintJobEntity) = db.printJobs().update(j)
    suspend fun setJobStatus(id: Long, status: String, sent: Int, error: String?) = db.printJobs().setStatus(id, status, sent, error)

    // ---------- bundles (backup / duplicate) ----------

    suspend fun exportBundle(businessId: Long): BusinessBundle {
        val b = db.businesses().get(businessId) ?: error("Business not found")
        return BusinessBundle(
            business = b,
            categories = db.categories().all(businessId),
            products = db.products().all(businessId),
            barcodes = db.barcodes().all(businessId),
            shelfRules = db.shelfLife().all(businessId),
            labelSizes = db.labelSizes().custom(businessId),
            designs = db.designs().allForBusiness(businessId).map { DesignBundle(it.design, it.elements) },
            printers = db.printers().all(businessId),
            jobs = db.printJobs().all(businessId),
            history = db.printJobs().allHistory(businessId),
        )
    }

    /** Imports a bundle as a NEW business with fresh ids. Never touches existing businesses. */
    suspend fun importBundle(bundle: BusinessBundle): Long = db.withTransaction {
        val newBiz = db.businesses().insert(bundle.business.copy(id = 0, defaultLabelSizeId = null, defaultPrinterId = null))
        val catMap = bundle.categories.associate { it.id to db.categories().insert(it.copy(id = 0, businessId = newBiz)) }
        val sizeMap = bundle.labelSizes.associate { it.id to db.labelSizes().insert(it.copy(id = 0, businessId = newBiz)) }
        val designMap = bundle.designs.associate { d ->
            d.design.id to db.designs().save(d.design.copy(id = 0, businessId = newBiz), d.elements)
        }
        val prodMap = bundle.products.associate { p ->
            p.id to db.products().insert(
                p.copy(id = 0, businessId = newBiz, categoryId = p.categoryId?.let { catMap[it] },
                    defaultDesignId = p.defaultDesignId?.let { designMap[it] ?: it })
            )
        }
        bundle.barcodes.forEach { bc ->
            prodMap[bc.productId]?.let { db.barcodes().insert(bc.copy(id = 0, productId = it, businessId = newBiz)) }
        }
        bundle.shelfRules.forEach { db.shelfLife().upsert(it.copy(id = 0, businessId = newBiz)) }
        val printerMap = bundle.printers.associate { it.id to db.printers().insert(it.copy(id = 0, businessId = newBiz)) }
        bundle.jobs.forEach { j ->
            val newJob = db.printJobs().insert(j.copy(id = 0, businessId = newBiz, printerId = j.printerId?.let { printerMap[it] }))
            db.printJobs().insertHistory(bundle.history.filter { it.jobId == j.id }.map {
                it.copy(id = 0, jobId = newJob, businessId = newBiz, productId = it.productId?.let { p -> prodMap[p] })
            })
        }
        val b = db.businesses().get(newBiz)!!
        db.businesses().update(
            b.copy(
                defaultLabelSizeId = bundle.business.defaultLabelSizeId?.let { sizeMap[it] ?: it },
                defaultPrinterId = bundle.business.defaultPrinterId?.let { printerMap[it] },
            )
        )
        newBiz
    }

    companion object {
        const val KEY_ACTIVE = "active_business"
        const val KEY_APP_LANGUAGE = "app_language"
    }
}
