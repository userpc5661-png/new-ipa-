package com.example.sls_assistant_pro.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavedAccountEntity::class,
        LocalContactEntity::class,
        DeliveryHistoryEntity::class,
        LocationCorrectionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedAccountDao(): SavedAccountDao
    abstract fun localContactDao(): LocalContactDao
    abstract fun deliveryHistoryDao(): DeliveryHistoryDao
    abstract fun locationCorrectionDao(): LocationCorrectionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sls_assistant_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
