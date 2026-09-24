package io.github.zyakusen.tsukiyo.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.zyakusen.tsukiyo.data.entity.DownloadItem
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE workId = :workId ORDER BY createdAt DESC")
    fun observeByWork(workId: Long): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE workId = :workId")
    suspend fun getByWork(workId: Long): List<DownloadItem>

    @Query("DELETE FROM downloads WHERE workId = :workId")
    suspend fun deleteByWorkId(workId: Long)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: String): DownloadItem?

    @Query("SELECT * FROM downloads WHERE status = :status")
    suspend fun getByStatus(status: Int): List<DownloadItem>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = :status")
    suspend fun countByStatus(status: Int): Int

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt ASC LIMIT 1")
    suspend fun getFirstByStatus(status: Int): DownloadItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadItem)

    @Update
    suspend fun update(item: DownloadItem)

    @Query("UPDATE downloads SET progress = :progress, downloadedBytes = :bytes WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Float, bytes: Long)

    @Query("UPDATE downloads SET downloadedBytes = :bytes WHERE id = :id")
    suspend fun updateDownloadedBytes(id: String, bytes: Long)

    @Delete
    suspend fun delete(item: DownloadItem)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE id = :id)")
    suspend fun exists(id: String): Boolean
}
