package io.github.zyakusen.tsukiyo.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey val workId: Long,
    val title: String,
    val circleName: String,
    val coverUrl: String?,
    val nsfw: Boolean,
    val viewedAt: Long
)
