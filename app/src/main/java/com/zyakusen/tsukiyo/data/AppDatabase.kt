package com.zyakusen.tsukiyo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zyakusen.tsukiyo.data.dao.DownloadDao
import com.zyakusen.tsukiyo.data.entity.DownloadItem

@Database(entities = [DownloadItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "asmr.db"
                ).build().also { instance = it }
            }
    }
}
