package com.foodlabel.studio.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [
        BusinessEntity::class,
        CategoryEntity::class,
        ProductEntity::class,
        ProductBarcodeEntity::class,
        ShelfLifeEntity::class,
        LabelSizeEntity::class,
        LabelDesignEntity::class,
        LabelElementEntity::class,
        PrinterProfileEntity::class,
        PrinterEntity::class,
        PrintJobEntity::class,
        PrintHistoryEntity::class,
        AppSettingsEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun businesses(): BusinessDao
    abstract fun categories(): CategoryDao
    abstract fun products(): ProductDao
    abstract fun barcodes(): BarcodeDao
    abstract fun shelfLife(): ShelfLifeDao
    abstract fun labelSizes(): LabelSizeDao
    abstract fun designs(): DesignDao
    abstract fun printers(): PrinterDao
    abstract fun printerProfiles(): PrinterProfileDao
    abstract fun printJobs(): PrintJobDao
    abstract fun settings(): SettingsDao

    companion object {
        const val VERSION = 1
        const val NAME = "foodlabel.db"

        /**
         * Every schema change adds a hand-written Migration here (never destructive fallback), and the
         * exported schema JSON in app/schemas is committed so migrations can be tested.
         * Example for the next version:
         *   val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db) { db.execSQL("ALTER TABLE ...") } }
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun create(context: Context): AppDatabase {
            safetyCopyBeforeUpgrade(context)
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .build()
        }

        /**
         * Before the first launch of a new app version, keep a copy of the database file
         * so user data can be recovered even if a migration were to fail.
         */
        private fun safetyCopyBeforeUpgrade(context: Context) {
            try {
                val prefs = context.getSharedPreferences("db_meta", Context.MODE_PRIVATE)
                val lastVersion = prefs.getInt("schema_version", -1)
                val dbFile = context.getDatabasePath(NAME)
                if (lastVersion != -1 && lastVersion != VERSION && dbFile.exists()) {
                    val dir = File(context.filesDir, "db-safety").apply { mkdirs() }
                    listOf("", "-wal", "-shm").forEach { suffix ->
                        val f = File(dbFile.path + suffix)
                        if (f.exists()) f.copyTo(File(dir, "v$lastVersion-$NAME$suffix"), overwrite = true)
                    }
                }
                prefs.edit().putInt("schema_version", VERSION).apply()
            } catch (e: Exception) {
                Log.w("AppDatabase", "Safety copy failed", e)
            }
        }
    }
}
