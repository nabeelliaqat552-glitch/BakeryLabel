package com.bakery.labelapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Product::class, Batch::class, LabelSettings::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun batches(): BatchDao
    abstract fun settings(): SettingsDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "bakery.db").build()
    }
}
