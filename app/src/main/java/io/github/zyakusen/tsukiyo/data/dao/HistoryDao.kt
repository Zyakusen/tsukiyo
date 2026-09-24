package io.github.zyakusen.tsukiyo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.zyakusen.tsukiyo.data.entity.HistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY viewedAt DESC")
    fun observeAll(): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItem)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE workId NOT IN (SELECT workId FROM history ORDER BY viewedAt DESC LIMIT :limit)")
    suspend fun trim(limit: Int)
}
