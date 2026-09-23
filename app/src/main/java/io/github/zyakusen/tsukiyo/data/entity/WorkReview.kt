package io.github.zyakusen.tsukiyo.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reviews")
data class WorkReview(
    @PrimaryKey val workId: Long,
    val rating: Double?,
    val reviewText: String?,
    val progress: String?
)
