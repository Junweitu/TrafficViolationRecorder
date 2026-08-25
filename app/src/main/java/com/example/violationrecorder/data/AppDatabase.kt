package com.example.violationrecorder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ViolationRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun violationRecordDao(): ViolationRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "violation_recorder_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
