package com.bakery.labelapp.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE active = 1 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY active DESC, sortOrder, name")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun get(id: String): Product?

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM products")
    suspend fun maxSort(): Int

    @Upsert
    suspend fun upsert(product: Product)

    @Upsert
    suspend fun upsertAll(products: List<Product>)

    @Query("SELECT * FROM products WHERE dirty = 1")
    suspend fun dirty(): List<Product>

    @Query("UPDATE products SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("UPDATE products SET dirty = 1")
    suspend fun markAllDirty()
}

@Dao
interface BatchDao {
    @Query("SELECT * FROM batches WHERE deleted = 0 ORDER BY createdAt DESC LIMIT 300")
    fun observeRecent(): Flow<List<Batch>>

    @Query("SELECT * FROM batches WHERE id = :id")
    suspend fun get(id: String): Batch?

    @Query("SELECT COUNT(*) FROM batches WHERE code LIKE :pattern")
    suspend fun countLike(pattern: String): Int

    @Upsert
    suspend fun upsert(batch: Batch)

    @Query("SELECT * FROM batches WHERE dirty = 1")
    suspend fun dirty(): List<Batch>

    @Query("UPDATE batches SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("UPDATE batches SET dirty = 1")
    suspend fun markAllDirty()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM label_settings WHERE id = 1")
    fun observe(): Flow<LabelSettings?>

    @Query("SELECT * FROM label_settings WHERE id = 1")
    suspend fun get(): LabelSettings?

    @Upsert
    suspend fun upsert(settings: LabelSettings)

    @Query("UPDATE label_settings SET dirty = 0 WHERE id = 1 AND updatedAt = :updatedAt")
    suspend fun markClean(updatedAt: Long)

    @Query("UPDATE label_settings SET dirty = 1")
    suspend fun markDirty()
}
