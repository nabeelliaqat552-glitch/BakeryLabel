package com.foodlabel.studio.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessDao {
    @Query("SELECT * FROM businesses WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<BusinessEntity>>

    @Query("SELECT * FROM businesses ORDER BY archived, name COLLATE NOCASE")
    fun observeAll(): Flow<List<BusinessEntity>>

    @Query("SELECT * FROM businesses WHERE id = :id")
    fun observe(id: Long): Flow<BusinessEntity?>

    @Query("SELECT * FROM businesses WHERE id = :id")
    suspend fun get(id: Long): BusinessEntity?

    @Query("SELECT * FROM businesses ORDER BY id")
    suspend fun all(): List<BusinessEntity>

    @Query("SELECT COUNT(*) FROM businesses")
    suspend fun count(): Int

    @Insert suspend fun insert(b: BusinessEntity): Long
    @Update suspend fun update(b: BusinessEntity)
    @Delete suspend fun delete(b: BusinessEntity)

    @Query("UPDATE businesses SET barcode_sequence = barcode_sequence + 1 WHERE id = :id")
    suspend fun bumpSequence(id: Long)

    @Query("SELECT barcode_sequence FROM businesses WHERE id = :id")
    suspend fun sequence(id: Long): Long
}

data class CategoryWithCount(
    val id: Long,
    val name: String,
    val nameUrdu: String,
    val iconKey: String,
    val tint: Long,
    val productCount: Int,
)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE business_id = :businessId ORDER BY sort_order, name COLLATE NOCASE")
    fun observe(businessId: Long): Flow<List<CategoryEntity>>

    @Query(
        """SELECT c.id AS id, c.name AS name, c.name_ur AS nameUrdu, c.icon_key AS iconKey, c.tint AS tint,
           (SELECT COUNT(*) FROM products p WHERE p.category_id = c.id AND p.business_id = :businessId) AS productCount
           FROM categories c WHERE c.business_id = :businessId ORDER BY c.sort_order, c.name COLLATE NOCASE"""
    )
    fun observeWithCounts(businessId: Long): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM categories WHERE business_id = :businessId ORDER BY sort_order")
    suspend fun all(businessId: Long): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id AND business_id = :businessId")
    suspend fun get(businessId: Long, id: Long): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM categories WHERE business_id = :businessId")
    suspend fun maxSort(businessId: Long): Int

    @Insert suspend fun insert(c: CategoryEntity): Long
    @Insert suspend fun insertAll(c: List<CategoryEntity>): List<Long>
    @Update suspend fun update(c: CategoryEntity)
    @Delete suspend fun delete(c: CategoryEntity)
}

@Dao
interface ProductDao {
    @Query(
        """SELECT * FROM products WHERE business_id = :businessId
           AND (:categoryId IS NULL OR category_id = :categoryId)
           AND (:favouritesOnly = 0 OR favourite = 1)
           AND (:q = '' OR name_en LIKE '%' || :q || '%' OR name_ur LIKE '%' || :q || '%' OR code LIKE '%' || :q || '%'
                OR id IN (SELECT product_id FROM product_barcodes WHERE business_id = :businessId AND value LIKE '%' || :q || '%'))
           ORDER BY favourite DESC, name_en COLLATE NOCASE"""
    )
    fun search(businessId: Long, q: String, categoryId: Long?, favouritesOnly: Boolean): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE business_id = :businessId ORDER BY name_en COLLATE NOCASE")
    suspend fun all(businessId: Long): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id AND business_id = :businessId")
    suspend fun get(businessId: Long, id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id AND business_id = :businessId")
    fun observe(businessId: Long, id: Long): Flow<ProductEntity?>

    @Query("SELECT COUNT(*) FROM products WHERE business_id = :businessId")
    fun observeCount(businessId: Long): Flow<Int>

    @Insert suspend fun insert(p: ProductEntity): Long
    @Update suspend fun update(p: ProductEntity)
    @Delete suspend fun delete(p: ProductEntity)

    @Query("UPDATE products SET favourite = :fav WHERE id = :id AND business_id = :businessId")
    suspend fun setFavourite(businessId: Long, id: Long, fav: Boolean)

    @Query("UPDATE products SET default_design_id = :designId WHERE id = :id AND business_id = :businessId")
    suspend fun setDesign(businessId: Long, id: Long, designId: Long?)
}

@Dao
interface BarcodeDao {
    @Query("SELECT * FROM product_barcodes WHERE product_id = :productId AND business_id = :businessId ORDER BY is_primary DESC")
    suspend fun forProduct(businessId: Long, productId: Long): List<ProductBarcodeEntity>

    @Query("SELECT * FROM product_barcodes WHERE business_id = :businessId")
    suspend fun all(businessId: Long): List<ProductBarcodeEntity>

    @Query("SELECT COUNT(*) FROM product_barcodes WHERE business_id = :businessId AND value = :value AND product_id != :exceptProductId")
    suspend fun countDuplicates(businessId: Long, value: String, exceptProductId: Long): Int

    @Query("DELETE FROM product_barcodes WHERE product_id = :productId AND business_id = :businessId")
    suspend fun deleteForProduct(businessId: Long, productId: Long)

    @Insert suspend fun insert(b: ProductBarcodeEntity): Long
}

@Dao
interface ShelfLifeDao {
    @Query("SELECT * FROM shelf_life_rules WHERE business_id = :businessId ORDER BY name")
    fun observe(businessId: Long): Flow<List<ShelfLifeEntity>>

    @Query("SELECT * FROM shelf_life_rules WHERE business_id = :businessId")
    suspend fun all(businessId: Long): List<ShelfLifeEntity>

    @Upsert suspend fun upsert(r: ShelfLifeEntity): Long
    @Delete suspend fun delete(r: ShelfLifeEntity)
}

@Dao
interface LabelSizeDao {
    @Query("SELECT * FROM label_sizes WHERE business_id IS NULL OR business_id = :businessId ORDER BY business_id IS NOT NULL, width_mm, height_mm")
    fun observe(businessId: Long): Flow<List<LabelSizeEntity>>

    @Query("SELECT * FROM label_sizes WHERE business_id IS NULL OR business_id = :businessId ORDER BY width_mm, height_mm")
    suspend fun all(businessId: Long): List<LabelSizeEntity>

    @Query("SELECT * FROM label_sizes WHERE business_id = :businessId")
    suspend fun custom(businessId: Long): List<LabelSizeEntity>

    @Query("SELECT * FROM label_sizes WHERE id = :id")
    suspend fun get(id: Long): LabelSizeEntity?

    @Query("SELECT COUNT(*) FROM label_sizes WHERE business_id IS NULL")
    suspend fun builtInCount(): Int

    @Insert suspend fun insert(s: LabelSizeEntity): Long
    @Insert suspend fun insertAll(s: List<LabelSizeEntity>)
    @Update suspend fun update(s: LabelSizeEntity)
    @Delete suspend fun delete(s: LabelSizeEntity)
}

data class DesignWithElements(
    @androidx.room.Embedded val design: LabelDesignEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "design_id")
    val elements: List<LabelElementEntity>,
)

@Dao
abstract class DesignDao {
    @Transaction
    @Query("SELECT * FROM label_designs WHERE is_template = 1 AND (business_id IS NULL OR business_id = :businessId) ORDER BY business_id IS NULL, group_name, width_mm")
    abstract fun observeTemplates(businessId: Long): Flow<List<DesignWithElements>>

    @Transaction
    @Query("SELECT * FROM label_designs WHERE is_template = 0 AND business_id = :businessId ORDER BY updated_at DESC")
    abstract fun observeSaved(businessId: Long): Flow<List<DesignWithElements>>

    @Transaction
    @Query("SELECT * FROM label_designs WHERE id = :id")
    abstract suspend fun get(id: Long): DesignWithElements?

    @Transaction
    @Query("SELECT * FROM label_designs WHERE business_id = :businessId")
    abstract suspend fun allForBusiness(businessId: Long): List<DesignWithElements>

    @Query("SELECT COUNT(*) FROM label_designs WHERE business_id IS NULL")
    abstract suspend fun builtInCount(): Int

    @Insert abstract suspend fun insertDesign(d: LabelDesignEntity): Long
    @Update abstract suspend fun updateDesign(d: LabelDesignEntity)
    @Delete abstract suspend fun deleteDesign(d: LabelDesignEntity)

    @Insert abstract suspend fun insertElements(e: List<LabelElementEntity>)

    @Query("DELETE FROM label_elements WHERE design_id = :designId")
    abstract suspend fun deleteElements(designId: Long)

    /** Saves a design and replaces its elements in one transaction. Returns the design id. */
    @Transaction
    open suspend fun save(design: LabelDesignEntity, elements: List<LabelElementEntity>): Long {
        val id = if (design.id == 0L) insertDesign(design) else { updateDesign(design); design.id }
        deleteElements(id)
        insertElements(elements.map { it.copy(id = 0, designId = id) })
        return id
    }
}

@Dao
interface PrinterDao {
    @Query("SELECT * FROM printers WHERE business_id = :businessId ORDER BY is_default DESC, name")
    fun observe(businessId: Long): Flow<List<PrinterEntity>>

    @Query("SELECT * FROM printers WHERE business_id = :businessId ORDER BY is_default DESC, name")
    suspend fun all(businessId: Long): List<PrinterEntity>

    @Query("SELECT * FROM printers WHERE id = :id AND business_id = :businessId")
    suspend fun get(businessId: Long, id: Long): PrinterEntity?

    @Query("UPDATE printers SET is_default = (id = :id) WHERE business_id = :businessId")
    suspend fun makeDefault(businessId: Long, id: Long)

    @Insert suspend fun insert(p: PrinterEntity): Long
    @Update suspend fun update(p: PrinterEntity)
    @Delete suspend fun delete(p: PrinterEntity)
}

@Dao
interface PrinterProfileDao {
    @Query("SELECT * FROM printer_profiles ORDER BY built_in DESC, name")
    fun observe(): Flow<List<PrinterProfileEntity>>

    @Query("SELECT COUNT(*) FROM printer_profiles WHERE built_in = 1")
    suspend fun builtInCount(): Int

    @Insert suspend fun insertAll(p: List<PrinterProfileEntity>)
    @Insert suspend fun insert(p: PrinterProfileEntity): Long
    @Delete suspend fun delete(p: PrinterProfileEntity)
}

@Dao
interface PrintJobDao {
    @Query("SELECT * FROM print_jobs WHERE business_id = :businessId ORDER BY created_at DESC LIMIT 300")
    fun observe(businessId: Long): Flow<List<PrintJobEntity>>

    @Query("SELECT * FROM print_jobs WHERE business_id = :businessId AND saved_name IS NOT NULL ORDER BY saved_name")
    fun observeSavedBatches(businessId: Long): Flow<List<PrintJobEntity>>

    @Query("SELECT * FROM print_jobs WHERE id = :id")
    suspend fun get(id: Long): PrintJobEntity?

    @Query("SELECT * FROM print_jobs WHERE business_id = :businessId")
    suspend fun all(businessId: Long): List<PrintJobEntity>

    @Insert suspend fun insert(j: PrintJobEntity): Long
    @Update suspend fun update(j: PrintJobEntity)

    @Query("UPDATE print_jobs SET status = :status, sent_labels = :sent, error = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, sent: Int, error: String?)

    /** Jobs left mid-way when the app was closed or the phone restarted. */
    @Query("UPDATE print_jobs SET status = 'FAILED', error = 'Stopped because the app was closed. Check the labels and print again if needed.' WHERE status IN ('QUEUED','SENDING')")
    suspend fun failInterrupted()

    @Insert suspend fun insertHistory(h: List<PrintHistoryEntity>)

    @Query("SELECT * FROM print_history WHERE job_id = :jobId")
    suspend fun historyFor(jobId: Long): List<PrintHistoryEntity>

    @Query("SELECT * FROM print_history WHERE business_id = :businessId")
    suspend fun allHistory(businessId: Long): List<PrintHistoryEntity>
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(s: AppSettingsEntity)
}
