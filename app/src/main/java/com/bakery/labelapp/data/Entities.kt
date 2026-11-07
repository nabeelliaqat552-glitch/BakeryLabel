package com.bakery.labelapp.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** A product the bakery sells, with its default shelf life. */
@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val shelfLifeDays: Int,
    val active: Boolean = true,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    /** true = changed locally and not yet handed to the cloud. */
    val dirty: Boolean = true,
)

/**
 * One production batch. Its dates are fixed when the batch is created and are
 * never recalculated, so an existing batch's expiry never moves when the day changes.
 */
@Entity(tableName = "batches", indices = [Index("productId"), Index("code")])
data class Batch(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val code: String,
    val productId: String,
    val productName: String,
    val mfgEpochDay: Long,
    val expEpochDay: Long,
    val expManual: Boolean,
    val copiesPrinted: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

/** Label design and shop settings (single row, id = 1). Colours are ARGB. */
@Entity(tableName = "label_settings")
data class LabelSettings(
    @PrimaryKey val id: Int = 1,
    val bakeryName: String = "BAKERY NAME",
    val subtitle: String = "FRESH & QUALITY PRODUCTS",
    val showSubtitle: Boolean = true,
    val widthMm: Float = 50f,
    val heightMm: Float = 30f,
    val gapMm: Float = 2f,
    val fontScale: Float = 1f,
    val borderMm: Float = 0.3f,
    val cornerMm: Float = 2.4f,
    val backgroundColor: Long = 0xFF000000,
    val textColor: Long = 0xFFFFFFFF,
    val borderColor: Long = 0xFFCFCFCF,
    val expiryColor: Long = 0xFFFF3B30,
    val dateFormat: String = "dd-MM-yyyy",
    val defaultCopies: Int = 1,
    val adminPin: String = "1234",
    val updatedAt: Long = 0,
    val dirty: Boolean = false,
)

val DEFAULT_PRODUCTS = listOf(
    "CHOCOLATE CAKE" to 2,
    "FRESH CREAM CAKE" to 2,
    "PLAIN RUSK" to 30,
    "MILK RUSK" to 30,
    "GULAB JAMUN" to 3,
    "BUTTER" to 15,
    "DESI GHEE" to 90,
    "PIZZA" to 1,
    "BURGER" to 1,
    "SANDWICH" to 1,
)
