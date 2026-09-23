package io.github.zyakusen.tsukiyo.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import io.github.zyakusen.tsukiyo.data.dao.DownloadDao
import io.github.zyakusen.tsukiyo.data.entity.DownloadItem
import io.github.zyakusen.tsukiyo.data.model.Track
import io.github.zyakusen.tsukiyo.data.model.Work
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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

    suspend fun enqueue(track: Track, work: Work, folderPath: String = "") {
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
            createdAt = System.currentTimeMillis(),
            folderPath = folderPath
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

    /** 递归下载整个作品的所有文件（音频/字幕/图片等），保留文件夹层级。 */
    suspend fun enqueueWork(work: Work, tracks: List<Track>): Int {
        var count = 0
        suspend fun walk(list: List<Track>, path: List<String>) {
            for (t in list) {
                when (t.type) {
                    "folder" -> t.children?.let { walk(it, path + listOf(t.title ?: "")) }
                    else -> {
                        enqueue(t, work, path.joinToString("/"))
                        count++
                    }
                }
            }
        }
        walk(tracks, emptyList())
        return count
    }

    /** 删除某作品下所有已下载文件与记录。 */
    suspend fun deleteWork(workId: Long) {
        val items = dao.getByWork(workId)
        items.forEach { it.localPath?.let { p -> runCatching { File(p).delete() } } }
        dao.deleteByWorkId(workId)
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

    /** 导出单个已下载文件到导出目录的「作品名」文件夹下。 */
    suspend fun exportFile(item: DownloadItem, exportUri: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val src = item.localPath?.let { File(it) } ?: throw IOException("文件不存在")
            if (!src.exists()) throw IOException("文件不存在")
            val root = DocumentFile.fromTreeUri(context, Uri.parse(exportUri)) ?: throw IOException("无法访问导出目录")
            val workDir = ensureDir(root, sanitize(item.workTitle))
            writeFile(workDir, sanitize(item.title), src)
            1
        }
    }

    /** 导出某作品全部已下载文件，保留文件夹层级。返回导出的文件数。 */
    suspend fun exportWork(workId: Long, exportUri: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val items = dao.getByWork(workId).filter { it.status == DownloadItem.STATUS_DONE && it.localPath != null }
            if (items.isEmpty()) throw IOException("没有已下载的文件")
            val root = DocumentFile.fromTreeUri(context, Uri.parse(exportUri)) ?: throw IOException("无法访问导出目录")
            val workDir = ensureDir(root, sanitize(items.first().workTitle))
            var count = 0
            for (item in items) {
                val src = File(item.localPath!!)
                if (!src.exists()) continue
                val targetDir = item.folderPath.split("/").filter { it.isNotBlank() }
                    .fold(workDir) { dir, name -> ensureDir(dir, sanitize(name)) }
                writeFile(targetDir, sanitize(item.title), src)
                count++
            }
            count
        }
    }

    private fun ensureDir(parent: DocumentFile, name: String): DocumentFile {
        parent.findFile(name)?.let { if (it.isDirectory) return it }
        return parent.createDirectory(name) ?: parent
    }

    private fun writeFile(dir: DocumentFile, name: String, src: File) {
        dir.findFile(name)?.let { runCatching { it.delete() } }
        val target = dir.createFile("application/octet-stream", name) ?: throw IOException("创建文件失败")
        context.contentResolver.openOutputStream(target.uri)?.use { out ->
            src.inputStream().use { it.copyTo(out) }
        } ?: throw IOException("无法写入文件")
    }

    private fun sanitize(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")

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
            "wav", "mp3", "m4a", "aac", "flac", "ogg", "opus", "wma",
            "lrc", "vtt", "srt", "ass", "ssa", "txt",
            "jpg", "jpeg", "png", "webp", "gif", "bmp",
            "pdf" -> last.lowercase()
            else -> "mp3"
        }
    }
}
