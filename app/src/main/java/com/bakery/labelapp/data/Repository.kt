package com.bakery.labelapp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class Repository(private val db: AppDatabase, private val cloud: CloudSync) {

    val activeProducts: Flow<List<Product>> = db.products().observeActive()
    val allProducts: Flow<List<Product>> = db.products().observeAll()
    val batches: Flow<List<Batch>> = db.batches().observeRecent()
    val settings: Flow<LabelSettings> = db.settings().observe().map { it ?: LabelSettings() }

    /** First launch: default settings and products. Seed rows use fixed ids and
     *  updatedAt = 0 so they never overwrite data already in the cloud. */
    suspend fun ensureSeed() {
        if (db.settings().get() == null) {
            db.settings().upsert(LabelSettings(updatedAt = 0, dirty = false))
        }
        if (db.products().count() == 0) {
            db.products().upsertAll(DEFAULT_PRODUCTS.mapIndexed { i, (name, days) ->
                Product(
                    id = "seed-" + name.lowercase().replace(' ', '-'),
                    name = name, shelfLifeDays = days, sortOrder = i,
                    updatedAt = 0, dirty = false,
                )
            })
        }
    }

    suspend fun saveProduct(p: Product) {
        val existing = db.products().get(p.id)
        val sort = existing?.sortOrder ?: (db.products().maxSort() + 1)
        db.products().upsert(
            p.copy(
                name = p.name.trim().uppercase(),
                sortOrder = sort,
                updatedAt = System.currentTimeMillis(),
                dirty = true,
            )
        )
        cloud.requestPush()
    }

    suspend fun saveSettings(s: LabelSettings) {
        db.settings().upsert(s.copy(id = 1, updatedAt = System.currentTimeMillis(), dirty = true))
        cloud.requestPush()
    }

    /** Creates a new batch with fixed dates. Code looks like B-071126-01. */
    suspend fun createBatch(product: Product, mfgDay: Long, expDay: Long, manual: Boolean, copies: Int): Batch {
        val d = LocalDate.ofEpochDay(mfgDay)
        val prefix = "B-%02d%02d%02d-".format(d.dayOfMonth, d.monthValue, d.year % 100)
        val next = db.batches().countLike("$prefix%") + 1
        val batch = Batch(
            code = prefix + "%02d".format(next),
            productId = product.id,
            productName = product.name,
            mfgEpochDay = mfgDay,
            expEpochDay = expDay,
            expManual = manual,
            copiesPrinted = copies,
        )
        db.batches().upsert(batch)
        cloud.requestPush()
        return batch
    }

    /** Reprint: only the print count changes, never the dates. */
    suspend fun addPrints(batch: Batch, copies: Int) {
        val current = db.batches().get(batch.id) ?: batch
        db.batches().upsert(
            current.copy(
                copiesPrinted = current.copiesPrinted + copies,
                updatedAt = System.currentTimeMillis(),
                dirty = true,
            )
        )
        cloud.requestPush()
    }

    suspend fun deleteBatch(batch: Batch) {
        db.batches().upsert(batch.copy(deleted = true, updatedAt = System.currentTimeMillis(), dirty = true))
        cloud.requestPush()
    }
}
