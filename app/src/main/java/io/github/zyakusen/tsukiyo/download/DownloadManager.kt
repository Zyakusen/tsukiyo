package io.github.zyakusen.tsukiyo.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import io.github.zyakusen.tsukiyo.data.dao.DownloadDao
import io.github.zyakusen.tsukiyo.data.entity.DownloadItem
import io.github.zyakusen.tsukiyo.data.model.Track
import io.github.zyakusen.tsukiyo.data.model.Work
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * 下载管理器：负责排队下载、观察状态与删除本地文件。
 */
class DownloadManager(
    private val context: Context,
    private val dao: DownloadDao
) {

    val downloadDir: File
        get() = File(context.getExternalFilesDir(null), "asmr_downloads").apply { mkdirs() }

    fun observeAll(): Flow<List<DownloadItem>> = dao.observeAll()

    fun observeByWork(workId: Long): Flow<List<DownloadItem>> = dao.observeByWork(workId)

    suspend fun isDownloaded(id: String): Boolean = dao.exists(id)

    suspend fun statusOf(id: String): DownloadItem? = dao.get(id)

    suspend fun enqueue(track: Track, work: Work) {
        val url = track.mediaDownloadUrl ?: track.mediaStreamUrl ?: return
        val id = track.hash ?: "${work.id ?: 0}_${track.title}"
        if (dao.exists(id)) return

        val fileName = buildFileName(work.sourceId, track.title, url)
        val item = DownloadItem(
            id = id,
            workId = work.id ?: 0L,
            workTitle = work.title ?: "",
            workCode = work.sourceId ?: "",
            title = track.title ?: "",
            url = url,
            fileName = fileName,
            coverUrl = work.coverUrl,
            duration = ((track.duration ?: 0.0) * 1000).toLong(),
            sizeBytes = track.size ?: 0L,
            status = DownloadItem.STATUS_QUEUED,
            progress = 0f,
            localPath = null,
            createdAt = System.currentTimeMillis()
        )
        dao.insert(item)

        val input = Data.Builder()
            .putString("id", id)
            .putString("url", url)
            .putString("fileName", fileName)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(input)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("download_$id", ExistingWorkPolicy.KEEP, request)
    }

    suspend fun retry(item: DownloadItem) {
        val input = Data.Builder()
            .putString("id", item.id)
            .putString("url", item.url)
            .putString("fileName", item.fileName)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(input)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("download_${item.id}", ExistingWorkPolicy.REPLACE, request)
        dao.update(item.copy(status = DownloadItem.STATUS_QUEUED, progress = 0f))
    }

    suspend fun delete(item: DownloadItem) {
        item.localPath?.let { runCatching { File(it).delete() } }
        dao.deleteById(item.id)
        runCatching { WorkManager.getInstance(context).cancelUniqueWork("download_${item.id}") }
    }

    private fun buildFileName(workCode: String?, title: String?, url: String): String {
        val ext = extensionOf(url)
        val base = title?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "track"
        val safe = base.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
        return if (workCode.isNullOrBlank()) "$safe.$ext" else "${workCode}_$safe.$ext"
    }

    private fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('.', "")
        return when (last.lowercase()) {
            "wav", "mp3", "m4a", "aac", "flac", "ogg", "opus", "wma" -> last.lowercase()
            else -> "mp3"
        }
    }
}
