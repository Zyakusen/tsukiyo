package io.github.zyakusen.tsukiyo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.zyakusen.tsukiyo.data.entity.WorkReview

@Dao
interface ReviewDao {

    @Query("SELECT * FROM reviews WHERE workId = :workId")
    suspend fun get(workId: Long): WorkReview?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WorkReview)
}
