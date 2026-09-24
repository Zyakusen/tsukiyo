package io.github.zyakusen.tsukiyo.download

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import io.github.zyakusen.tsukiyo.data.api.NetworkModule
import io.github.zyakusen.tsukiyo.data.dao.DownloadDao
import io.github.zyakusen.tsukiyo.data.entity.DownloadItem
import io.github.zyakusen.tsukiyo.data.model.Track
import io.github.zyakusen.tsukiyo.data.model.Work
import io.github.zyakusen.tsukiyo.util.baseName
import io.github.zyakusen.tsukiyo.util.isAudioFile
import io.github.zyakusen.tsukiyo.util.isVideoFile
import io.github.zyakusen.tsukiyo.util.vttToLrc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.IOException

/** 导出选项。 */
data class ExportOptions(
    val convertSubtitles: Boolean = false,
    val includeCover: Boolean = false
)

private const val EXPORT_NOTIFICATION_ID = 2000

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

        val fileName = buildFileName(work.sourceId, folderPath, track.title, url)
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

        DownloadQueue.scheduleNext(context, dao)
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
        items.forEach {
            it.localPath?.let { p -> runCatching { File(p).delete() } }
            runCatching { File(downloadDir, "${it.fileName}.part").delete() }
            DownloadQueue.cancel(context, it.id)
        }
        dao.deleteByWorkId(workId)
        DownloadQueue.scheduleNext(context, dao)
    }

    suspend fun retry(item: DownloadItem) {
        dao.update(item.copy(status = DownloadItem.STATUS_QUEUED, progress = 0f))
        DownloadQueue.scheduleNext(context, dao)
    }

    suspend fun delete(item: DownloadItem) {
        item.localPath?.let { runCatching { File(it).delete() } }
        runCatching { File(downloadDir, "${item.fileName}.part").delete() }
        dao.deleteById(item.id)
        DownloadQueue.cancel(context, item.id)
        DownloadQueue.scheduleNext(context, dao)
    }

    suspend fun deleteById(id: String) {
        dao.get(id)?.let { delete(it) }
    }

    /** 暂停下载：标记暂停并取消对应任务，Worker 会在停止前落盘已下载字节。 */
    suspend fun pause(id: String) {
        val item = dao.get(id) ?: return
        if (item.status != DownloadItem.STATUS_DOWNLOADING && item.status != DownloadItem.STATUS_QUEUED) return
        dao.update(item.copy(status = DownloadItem.STATUS_PAUSED))
        DownloadQueue.cancel(context, id)
        DownloadQueue.scheduleNext(context, dao)
    }

    /** 继续下载：从断点（downloadedBytes）重新入队。 */
    suspend fun resume(id: String) {
        val item = dao.get(id) ?: return
        if (item.status != DownloadItem.STATUS_PAUSED && item.status != DownloadItem.STATUS_FAILED) return
        dao.update(item.copy(status = DownloadItem.STATUS_QUEUED))
        DownloadQueue.scheduleNext(context, dao)
    }

    /** 导出单个已下载文件到导出目录的「作品名」文件夹下。 */
    suspend fun exportFile(item: DownloadItem, exportUri: String, options: ExportOptions = ExportOptions()): Result<Int> = withContext(Dispatchers.IO) {
        notifyExport("正在导出「${item.workTitle}」…", true)
        runCatching {
            val src = item.localPath?.let { File(it) } ?: throw IOException("文件不存在")
            if (!src.exists()) throw IOException("文件不存在")
            val root = DocumentFile.fromTreeUri(context, Uri.parse(exportUri)) ?: throw IOException("无法访问导出目录")
            val workDir = ensureDir(root, sanitize(item.workTitle))
            val isAudio = isAudioFile(item.title)
            if (isAudio && options.convertSubtitles) {
                val siblings = dao.getByWork(item.workId).filter { it.status == DownloadItem.STATUS_DONE && it.localPath != null }
                val vtt = subtitleItemFor(item, siblings)?.localPath?.let { File(it) }
                exportAudio(src, workDir, item.title, vtt)
            } else {
                writeFile(workDir, sanitize(item.title), src)
            }
            if (options.includeCover && isAudio && !isVideoFile(item.title) && !item.coverUrl.isNullOrBlank()) {
                writeCover(workDir, item.coverUrl)
            }
            1
        }.onSuccess {
            notifyExport("「${item.workTitle}」导出完成", false)
        }.onFailure {
            notifyExport("导出失败：${it.message}", false)
        }
    }

    /** 导出某作品全部已下载文件，保留文件夹层级。返回导出的文件数。 */
    suspend fun exportWork(workId: Long, exportUri: String, options: ExportOptions = ExportOptions()): Result<Int> = withContext(Dispatchers.IO) {
        notifyExport("正在导出作品…", true)
        runCatching {
            val items = dao.getByWork(workId).filter { it.status == DownloadItem.STATUS_DONE && it.localPath != null }
            if (items.isEmpty()) throw IOException("没有已下载的文件")
            val root = DocumentFile.fromTreeUri(context, Uri.parse(exportUri)) ?: throw IOException("无法访问导出目录")
            val workDir = ensureDir(root, sanitize(items.first().workTitle))
            val coverUrl = items.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
            val audioFolders = mutableSetOf<String>()
            var count = 0
            for (item in items) {
                val src = File(item.localPath!!)
                if (!src.exists()) continue
                // 选择了转换字幕时，跳过原 vtt/srt（已转为同名 lrc）
                if (options.convertSubtitles && isConvertibleSubtitle(item.title)) continue
                val targetDir = item.folderPath.split("/").filter { it.isNotBlank() }
                    .fold(workDir) { dir, name -> ensureDir(dir, sanitize(name)) }
                val isAudio = isAudioFile(item.title)
                if (isAudio && !isVideoFile(item.title)) audioFolders.add(item.folderPath)
                if (isAudio && options.convertSubtitles) {
                    val vtt = subtitleItemFor(item, items)?.localPath?.let { File(it) }
                    exportAudio(src, targetDir, item.title, vtt)
                } else {
                    writeFile(targetDir, sanitize(item.title), src)
                }
                count++
            }
            if (options.includeCover && coverUrl != null) {
                for (fp in audioFolders) {
                    val dir = fp.split("/").filter { it.isNotBlank() }
                        .fold(workDir) { d, name -> ensureDir(d, sanitize(name)) }
                    writeCover(dir, coverUrl)
                }
            }
            count
        }.onSuccess { n ->
            notifyExport("导出完成（$n 个文件）", false)
        }.onFailure {
            notifyExport("导出失败：${it.message}", false)
        }
    }

    /** 导出进度通知（不定进度）。 */
    private fun notifyExport(text: String, ongoing: Boolean) {
        runCatching {
            val builder = NotificationCompat.Builder(context, "asmr_download")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("Tsukiyo")
                .setContentText(text)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, ongoing)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(EXPORT_NOTIFICATION_ID, builder.build())
        }
    }

    private fun subtitleItemFor(audio: DownloadItem, items: List<DownloadItem>): DownloadItem? =
        items.firstOrNull {
            it.id != audio.id &&
                it.workId == audio.workId &&
                it.folderPath == audio.folderPath &&
                it.status == DownloadItem.STATUS_DONE &&
                it.localPath != null &&
                (it.title == audio.title + ".vtt" || it.title == audio.title + ".srt")
        }

    private fun lrcFileName(audioTitle: String): String = baseName(audioTitle) + ".lrc"

    private fun isConvertibleSubtitle(title: String): Boolean {
        val t = title.lowercase()
        return t.endsWith(".vtt") || t.endsWith(".srt")
    }

    /** 导出音频：若存在同名 vtt/srt 则转为 lrc 并内嵌歌词，再写入音频与 lrc 文件。 */
    private fun exportAudio(src: File, targetDir: DocumentFile, audioTitle: String, vttFile: File?) {
        val vtt = vttFile?.takeIf { it.exists() }
        if (vtt == null) {
            writeFile(targetDir, sanitize(audioTitle), src)
            return
        }
        val lrc = runCatching { vttToLrc(vtt.readText()) }.getOrNull().orEmpty()
        if (lrc.isBlank()) {
            writeFile(targetDir, sanitize(audioTitle), src)
            return
        }
        val temp = File(context.cacheDir, "export_" + System.nanoTime() + "_" + sanitize(audioTitle))
        src.copyTo(temp, overwrite = true)
        try {
            embedLyrics(temp, lrc)
            writeText(targetDir, lrcFileName(audioTitle), lrc)
            writeFile(targetDir, sanitize(audioTitle), temp)
        } finally {
            runCatching { temp.delete() }
        }
    }

    /** 用 jaudiotagger 将歌词文本写入音频元数据（mp3=USLT、flac/ogg=LYRICS、m4a=©lyr，wav=ID3 chunk）。 */
    private fun embedLyrics(file: File, text: String) {
        runCatching {
            val af = AudioFileIO.read(file)
            val tag = af.tag ?: return@runCatching
            tag.setField(FieldKey.LYRICS, text)
            af.commit()
        }
    }

    private fun writeText(dir: DocumentFile, name: String, content: String) {
        dir.findFile(name)?.let { runCatching { it.delete() } }
        val target = dir.createFile("application/octet-stream", name) ?: throw IOException("创建文件失败")
        context.contentResolver.openOutputStream(target.uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("无法写入文件")
    }

    /** 下载作品封面并写入目录下的 folder.<ext>（沿用音乐文件夹封面的约定命名）。 */
    private fun writeCover(dir: DocumentFile, coverUrl: String) {
        runCatching {
            val req = okhttp3.Request.Builder().url(coverUrl).build()
            NetworkModule.downloadClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching
                val bytes = resp.body?.bytes() ?: return@runCatching
                val name = coverFileName(coverUrl)
                dir.findFile(name)?.let { runCatching { it.delete() } }
                val target = dir.createFile("image/*", name) ?: return@runCatching
                context.contentResolver.openOutputStream(target.uri)?.use { it.write(bytes) }
            }
        }
    }

    private fun coverFileName(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")) "folder.$ext" else "folder.jpg"
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

    private fun buildFileName(workCode: String?, folderPath: String, title: String?, url: String): String {
        val ext = extensionOf(url)
        val base = title?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "track"
        val safe = base.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
        val folder = folderPath.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val prefix = if (folder.isBlank()) "" else "${folder}_"
        return if (workCode.isNullOrBlank()) "${prefix}$safe.$ext" else "${workCode}_${prefix}$safe.$ext"
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
