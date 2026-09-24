package io.github.zyakusen.tsukiyo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.zyakusen.tsukiyo.data.dao.DownloadDao
import io.github.zyakusen.tsukiyo.data.dao.HistoryDao
import io.github.zyakusen.tsukiyo.data.dao.ReviewDao
import io.github.zyakusen.tsukiyo.data.entity.DownloadItem
import io.github.zyakusen.tsukiyo.data.entity.HistoryItem
import io.github.zyakusen.tsukiyo.data.entity.WorkReview

@Database(entities = [DownloadItem::class, WorkReview::class, HistoryItem::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun reviewDao(): ReviewDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reviews` (" +
                        "`workId` INTEGER NOT NULL, " +
                        "`rating` REAL, " +
                        "`reviewText` TEXT, " +
                        "`progress` TEXT, " +
                        "PRIMARY KEY(`workId`))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `folderPath` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `history` (" +
                        "`workId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`circleName` TEXT NOT NULL, " +
                        "`coverUrl` TEXT, " +
                        "`nsfw` INTEGER NOT NULL, " +
                        "`viewedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`workId`))"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `downloadedBytes` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "asmr.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}
