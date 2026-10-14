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
    val viewedAt: Long,
    /** 声优（用 " / " 拼好的名字，历史卡片上显示）。老记录为空 —— 重新访问该作品后才会补上。 */
    val vas: String = ""
)
