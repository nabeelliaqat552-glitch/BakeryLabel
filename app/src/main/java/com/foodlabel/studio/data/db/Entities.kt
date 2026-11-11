package com.foodlabel.studio.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * Every business-specific table carries business_id with ON DELETE CASCADE, and every DAO query
 * filters by it, so data from two businesses can never mix.
 * Built-in rows (templates, label sizes, printer profiles) use business_id = NULL.
 */

@Serializable
@Entity(tableName = "businesses")
data class BusinessEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "name_ur") val nameUrdu: String = "",
    val address: String = "",
    val phone: String = "",
    @ColumnInfo(name = "logo_path") val logoPath: String? = null,
    @ColumnInfo(name = "currency_symbol") val currencySymbol: String = "Rs",
    @ColumnInfo(name = "currency_code") val currencyCode: String = "PKR",
    @ColumnInfo(name = "date_format") val dateFormat: String = "DMY_DASH",
    @ColumnInfo(name = "show_mfg_time") val showMfgTime: Boolean = false,
    @ColumnInfo(name = "default_label_size_id") val defaultLabelSizeId: Long? = null,
    @ColumnInfo(name = "default_printer_id") val defaultPrinterId: Long? = null,
    @ColumnInfo(name = "label_language") val labelLanguage: String = "BOTH",
    @ColumnInfo(name = "barcode_sequence") val barcodeSequence: Long = 0,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(BusinessEntity::class, ["id"], ["business_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("business_id")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "business_id") val businessId: Long,
    val name: String,
    @ColumnInfo(name = "name_ur") val nameUrdu: String = "",
    @ColumnInfo(name = "icon_key") val iconKey: String = "box",
    val tint: Long = 0xFFEFE5D3,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "default_shelf_life") val defaultShelfLife: Int? = null,
    @ColumnInfo(name = "default_shelf_unit") val defaultShelfUnit: String = "DAYS",
)

@Serializable
@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(BusinessEntity::class, ["id"], ["business_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CategoryEntity::class, ["id"], ["category_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("business_id"), Index("category_id"), Index(value = ["business_id", "name_en"])],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "business_id") val businessId: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "name_ur") val nameUr: String = "",
    val code: String = "",
    @ColumnInfo(name = "image_path") val imagePath: String? = null,
    val price: Double? = null,
    val weight: String = "",
    @ColumnInfo(name = "batch_number") val batchNumber: String = "",
    @ColumnInfo(name = "shelf_life") val shelfLife: Int? = null,
    @ColumnInfo(name = "shelf_unit") val shelfUnit: String = "DAYS",
    val favourite: Boolean = false,
    /** Comma-separated field keys hidden on this product's labels, e.g. "batch,weight". */
    @ColumnInfo(name = "hidden_fields") val hiddenFields: String = "",
    /** JSON object of custom name -> value pairs. */
    @ColumnInfo(name = "custom_fields") val customFields: String = "{}",
    @ColumnInfo(name = "default_design_id") val defaultDesignId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "product_barcodes",
    foreignKeys = [
        ForeignKey(ProductEntity::class, ["id"], ["product_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(BusinessEntity::class, ["id"], ["business_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("product_id"), Index("business_id"), Index(value = ["business_id", "value"])],
)
data class ProductBarcodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "business_id") val businessId: Long,
    val type: String,
    val value: String,
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean = true,
    /** True when the app generated it (in-store prefix 2, not GS1 allocated). */
    @ColumnInfo(name = "is_internal") val isInternal: Boolean = false,
)

@Serializable
@Entity(
    tableName = "shelf_life_rules",
    foreignKeys = [ForeignKey(BusinessEntity::class, ["id"], ["business_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("business_id")],
)
data class ShelfLifeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "business_id") val businessId: Long,
    val name: String,
    val value: Int,
    val unit: String = "DAYS",
)

@Serializable
@Entity(
    tableName = "label_sizes",
    foreignKeys = [ForeignKey(BusinessEntity::class, ["id"], ["business_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("business_id")],
)
data class LabelSizeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** NULL = built-in size shown to every business. */
    @ColumnInfo(name = "business_id") val businessId: Long? = null,
    val name: String,
    @ColumnInfo(name = "width_mm") val widthMm: Double,
    @ColumnInfo(name = "height_mm") val heightMm: Double,
    @ColumnInfo(name = "gap_mm") val gapMm: Double = 2.0,
    @ColumnInfo(name = "margin_h_mm") val marginHMm: Double = 1.0,
    @ColumnInfo(name = "margin_v_mm") val marginVMm: Double = 1.0,
    @ColumnInfo(name = "paper_width_mm") val paperWidthMm: Double? = null,
    val continuous: Boolean = false,
    /** RECT or ROUND */
    val shape: String = "RECT",
    /** PORTRAIT or LANDSCAPE (how the label is fed). */
    val orientation: String = "LANDSCAPE",
    /** MM or INCH, for display only. */
    @ColumnInfo(name = "display_unit") val displayUnit: String = "MM",
)

@Serializable
@Entity(
    tableName = "label_designs",
    foreignKeys = [ForeignKey(BusinessEntity::class, ["id"], ["business_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("business_id"), Index("is_template")],
)
data class LabelDesignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** NULL = built-in template. */
    @ColumnInfo(name = "business_id") val businessId: Long? = null,
    val name: String,
    /** Template library group, e.g. "Minimal Bakery". */
    @ColumnInfo(name = "group_name") val groupName: String = "Custom",
    @ColumnInfo(name = "is_template") val isTemplate: Boolean = false,
    @ColumnInfo(name = "width_mm") val widthMm: Double,
    @ColumnInfo(name = "height_mm") val heightMm: Double,
    val shape: String = "RECT",
    @ColumnInfo(name = "border_mm") val borderMm: Double = 0.0,
    @ColumnInfo(name = "corner_mm") val cornerMm: Double = 0.0,
    @ColumnInfo(name = "inset_mm") val insetMm: Double = 0.0,
    @ColumnInfo(name = "background_black") val backgroundBlack: Boolean = false,
    @ColumnInfo(name = "built_in_key") val builtInKey: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "label_elements",
    foreignKeys = [ForeignKey(LabelDesignEntity::class, ["id"], ["design_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("design_id")],
)
data class LabelElementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "design_id") val designId: Long,
    /** TEXT, BARCODE, QR, IMAGE, LINE, BOX */
    val type: String,
    @ColumnInfo(name = "x_mm") val xMm: Double,
    @ColumnInfo(name = "y_mm") val yMm: Double,
    @ColumnInfo(name = "w_mm") val wMm: Double,
    @ColumnInfo(name = "h_mm") val hMm: Double,
    val rotation: Int = 0,
    val z: Int = 0,
    /** Text with tokens such as {name_en} or "EXP {exp}". */
    val content: String = "",
    @ColumnInfo(name = "font_pt") val fontPt: Double = 8.0,
    @ColumnInfo(name = "font_family") val fontFamily: String = "SANS",
    val bold: Boolean = false,
    val italic: Boolean = false,
    /** START, CENTER, END */
    val align: String = "START",
    val visible: Boolean = true,
    val inverted: Boolean = false,
    @ColumnInfo(name = "stroke_mm") val strokeMm: Double = 0.3,
    @ColumnInfo(name = "barcode_type") val barcodeType: String = "CODE_128",
    @ColumnInfo(name = "human_readable") val humanReadable: Boolean = true,
    @ColumnInfo(name = "image_path") val imagePath: String? = null,
    @ColumnInfo(name = "max_lines") val maxLines: Int = 2,
)

@Serializable
@Entity(tableName = "printer_profiles")
data class PrinterProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: String,
    val dpi: Int = 203,
    @ColumnInfo(name = "head_width_mm") val headWidthMm: Double = 48.0,
    @ColumnInfo(name = "supports_gap") val supportsGap: Boolean = true,
    val notes: String = "",
    @ColumnInfo(name = "built_in") val builtIn: Boolean = false,
)

@Serializable
@Entity(
    tableName = "printers",
    foreignKeys = [ForeignKey(BusinessEntity::class, ["id"], ["business_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("business_id")],
)
data class PrinterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "business_id") val businessId: Long,
    val name: String,
    /** BLUETOOTH, BLE, USB, NETWORK, ANDROID_PRINT */
    val connection: String,
    /** MAC address, USB device name, or IP address. */
    val address: String = "",
    val port: Int = 9100,
    val protocol: String = "TSPL",
    val dpi: Int = 203,
    @ColumnInfo(name = "head_width_mm") val headWidthMm: Double = 48.0,
    val density: Int = 8,
    val speed: Int = 3,
    @ColumnInfo(name = "offset_x_mm") val offsetXmm: Double = 0.0,
    @ColumnInfo(name = "profile_id") val profileId: Long? = null,
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    /** True only after the user confirmed a test label printed correctly. */
    val verified: Boolean = false,
)

@Serializable
@Entity(
    tableName = "print_jobs",
    foreignKeys = [ForeignKey(BusinessEntity::class, ["id"], ["business_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("business_id")],
)
data class PrintJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "business_id") val businessId: Long,
    @ColumnInfo(name = "printer_id") val printerId: Long?,
    @ColumnInfo(name = "printer_name") val printerName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    /** QUEUED, SENDING, SENT, CONFIRMED, FAILED, CANCELLED */
    val status: String = "QUEUED",
    @ColumnInfo(name = "total_labels") val totalLabels: Int,
    @ColumnInfo(name = "sent_labels") val sentLabels: Int = 0,
    val error: String? = null,
    /** JSON of the batch items, used for "Print again". */
    val payload: String = "[]",
    @ColumnInfo(name = "saved_name") val savedName: String? = null,
)

@Serializable
@Entity(
    tableName = "print_history",
    foreignKeys = [
        ForeignKey(PrintJobEntity::class, ["id"], ["job_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(BusinessEntity::class, ["id"], ["business_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("job_id"), Index("business_id")],
)
data class PrintHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "job_id") val jobId: Long,
    @ColumnInfo(name = "business_id") val businessId: Long,
    @ColumnInfo(name = "product_id") val productId: Long?,
    @ColumnInfo(name = "product_name") val productName: String,
    val copies: Int,
    @ColumnInfo(name = "mfg_date") val mfgDate: String,
    @ColumnInfo(name = "exp_date") val expDate: String,
    @ColumnInfo(name = "batch_number") val batchNumber: String,
    @ColumnInfo(name = "design_name") val designName: String,
)

@Serializable
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val key: String,
    val value: String,
)
