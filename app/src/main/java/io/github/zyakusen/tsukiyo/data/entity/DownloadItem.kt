package io.github.zyakusen.tsukiyo.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey val id: String,
    val workId: Long,
    val workTitle: String,
    val workCode: String,
    val title: String,
    val url: String,
    val fileName: String,
    val coverUrl: String?,
    val duration: Long,
    val sizeBytes: Long,
    val status: Int, // 0 排队 1 下载中 2 完成 3 失败 4 已暂停
    val progress: Float,
    val localPath: String?,
    val createdAt: Long,
    val folderPath: String = "",
    val downloadedBytes: Long = 0L
) {
    companion object {
        const val STATUS_QUEUED = 0
        const val STATUS_DOWNLOADING = 1
        const val STATUS_DONE = 2
        const val STATUS_FAILED = 3
        const val STATUS_PAUSED = 4
    }
}
