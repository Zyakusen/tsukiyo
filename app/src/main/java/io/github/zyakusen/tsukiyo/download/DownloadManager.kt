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
import io.github.zyakusen.tsukiyo.util.subtitleNamesFor
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

    /**
     * 导出结果概要。
     *
     * `files` 是**实际写出的文件数**（含由 vtt/srt 转换出来的 `.lrc`），所以
     * 「下载了音频+字幕」导出的结果是 2 个文件（音频 + lrc），而不是 1 个。
     */
    data class ExportSummary(
        val single: Boolean,
        val files: Int,
        val audios: Int,
        val embedded: Int,
        val failed: Int,
        val noSubtitle: Int
    ) {
        val text: String
            get() = buildString {
                if (single) {
                    append("导出完成")
                    when {
                        embedded > 0 -> append("（已内嵌字幕，lrc 已放在音频同级）")
                        failed > 0 -> append("（字幕未能内嵌，lrc 已放在音频同级）")
                        audios > 0 -> append("（无对应字幕）")
                        else -> Unit
                    }
                } else {
                    append("导出完成：$files 个文件")
                    if (audios > 0) {
                        append("（音频 $audios：内嵌字幕 $embedded")
                        if (failed > 0) append("，未内嵌 $failed")
                        if (noSubtitle > 0) append("，无对应字幕 $noSubtitle")
                        append("）")
                    }
                }
            }
    }

    /** 导出单个已下载文件到导出目录的「作品名」文件夹下。 */
    suspend fun exportFile(item: DownloadItem, exportUri: String, options: ExportOptions = ExportOptions()): Result<ExportSummary> = withContext(Dispatchers.IO) {
        notifyExport("正在导出「${item.workTitle}」…", true)
        var summary = ExportSummary(single = true, files = 0, audios = 0, embedded = 0, failed = 0, noSubtitle = 0)
        runCatching {
            val src = item.localPath?.let { File(it) } ?: throw IOException("文件不存在")
            if (!src.exists()) throw IOException("文件不存在")
            val root = DocumentFile.fromTreeUri(context, Uri.parse(exportUri)) ?: throw IOException("无法访问导出目录")
            val workDir = ensureDir(root, sanitize(item.workTitle))
            val isAudio = isAudioFile(item.title) && !isVideoFile(item.title)
            var files = 1
            var embedded = 0
            var failed = 0
            var noSubtitle = 0
            if (isAudio && options.convertSubtitles) {
                val siblings = dao.getByWork(item.workId).filter { it.status == DownloadItem.STATUS_DONE && it.localPath != null }
                val sub = subtitleItemFor(item, siblings)?.localPath?.let { File(it) }
                val r = exportAudio(src, workDir, item.title, sub)
                if (r.wroteLrc) files++
                when (r.subtitle) {
                    SubtitleResult.EMBEDDED -> embedded++
                    SubtitleResult.FAILED -> failed++
                    SubtitleResult.NONE -> noSubtitle++
                }
            } else {
                writeFile(workDir, sanitize(item.title), src)
            }
            if (options.includeCover && isAudio && !item.coverUrl.isNullOrBlank()) {
                writeCover(workDir, item.coverUrl)
            }
            summary = ExportSummary(true, files, if (isAudio) 1 else 0, embedded, failed, noSubtitle)
            summary
        }.onSuccess { s ->
            notifyExport("「${item.workTitle}」${s.text}", false)
        }.onFailure {
            notifyExport("导出失败：${it.message}", false)
        }
    }

    /** 导出某作品全部已下载文件，保留文件夹层级。 */
    suspend fun exportWork(workId: Long, exportUri: String, options: ExportOptions = ExportOptions()): Result<ExportSummary> = withContext(Dispatchers.IO) {
        notifyExport("正在导出作品…", true)
        // 统计：共写出几个文件、其中音频几个、成功转换并内嵌字幕几个、有字幕但失败几个、无字幕几个
        var files = 0
        var audios = 0
        var embedded = 0
        var failed = 0
        var noSubtitle = 0
        runCatching {
            val items = dao.getByWork(workId).filter { it.status == DownloadItem.STATUS_DONE && it.localPath != null }
            if (items.isEmpty()) throw IOException("没有已下载的文件")
            val root = DocumentFile.fromTreeUri(context, Uri.parse(exportUri)) ?: throw IOException("无法访问导出目录")
            val workDir = ensureDir(root, sanitize(items.first().workTitle))
            val coverUrl = items.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
            val audioFolders = mutableSetOf<String>()
            for (item in items) {
                val src = File(item.localPath!!)
                if (!src.exists()) continue
                // 选择了转换字幕时，跳过原 vtt/srt（已转为同名 lrc；lrc 本身仍会导出）
                if (options.convertSubtitles && isConvertibleSubtitle(item.title)) continue
                val targetDir = item.folderPath.split("/").filter { it.isNotBlank() }
                    .fold(workDir) { dir, name -> ensureDir(dir, sanitize(name)) }
                val itemIsAudio = isAudioFile(item.title) && !isVideoFile(item.title)
                if (itemIsAudio) audioFolders.add(item.folderPath)
                if (itemIsAudio && options.convertSubtitles) {
                    val sub = subtitleItemFor(item, items)?.localPath?.let { File(it) }
                    val r = exportAudio(src, targetDir, item.title, sub)
                    // 写出的是音频本身，有字幕时还会多一个同名 lrc
                    files += 1 + (if (r.wroteLrc) 1 else 0)
                    audios++
                    when (r.subtitle) {
                        SubtitleResult.EMBEDDED -> embedded++
                        SubtitleResult.FAILED -> failed++
                        SubtitleResult.NONE -> noSubtitle++
                    }
                } else {
                    writeFile(targetDir, sanitize(item.title), src)
                    files++
                }
            }
            if (options.includeCover && coverUrl != null) {
                for (fp in audioFolders) {
                    val dir = fp.split("/").filter { it.isNotBlank() }
                        .fold(workDir) { d, name -> ensureDir(d, sanitize(name)) }
                    writeCover(dir, coverUrl)
                }
            }
            ExportSummary(false, files, audios, embedded, failed, noSubtitle)
        }.onSuccess { s ->
            notifyExport(s.text, false)
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

    /**
     * 找与音频匹配的已下载字幕，**规则与播放时完全一致**（同一份 `subtitleNamesFor`）：
     *  ① 先在本级目录找同名候选（`xxx.mp3.vtt` / `xxx.srt` / `xxx.lrc` …）；
     *  ② 找不到再在**同一作品的其他目录**里找 —— 有的社团把 lrc 单独放在一个文件夹里。
     */
    private fun subtitleItemFor(audio: DownloadItem, items: List<DownloadItem>): DownloadItem? {
        val candidates = subtitleNamesFor(audio.title)
        fun usable(it: DownloadItem) = it.id != audio.id &&
            it.workId == audio.workId &&
            it.status == DownloadItem.STATUS_DONE &&
            !it.localPath.isNullOrBlank() &&
            it.title in candidates
        return items.firstOrNull { usable(it) && it.folderPath == audio.folderPath }
            ?: items.firstOrNull { usable(it) }
    }

    private fun lrcFileName(audioTitle: String): String = baseName(audioTitle) + ".lrc"

    /**
     * jaudiotagger 能读写标签的扩展名。
     * 实测：mp3 / flac / m4a / ogg / wav 均可；**opus 与 aac 会抛
     * "No Reader associated with this extension"**，所以不做无谓尝试。
     */
    private val EMBEDDABLE_EXTENSIONS = setOf("mp3", "m4a", "mp4", "flac", "ogg", "oga", "wav", "aif", "aiff", "wma")

    private fun isConvertibleSubtitle(title: String): Boolean {
        val t = title.lowercase()
        return t.endsWith(".vtt") || t.endsWith(".srt")
    }

    /** 一个字幕在导出时的处理结果。 */
    private enum class SubtitleResult { EMBEDDED, FAILED, NONE }

    /**
     * 从字幕文件得到 LRC 文本：
     * **vtt / srt 需要转换；本来就是 lrc 的直接原样使用**（以前只认 vtt/srt，
     * 于是"社团已经把 lrc 放在音频旁边"这种情况会白跑一趟、内嵌不上）。
     */
    private fun lyricsFromSubtitle(file: File): String = runCatching {
        when {
            isConvertibleSubtitle(file.name) -> vttToLrc(file.readText())
            file.name.lowercase().endsWith(".lrc") -> file.readText()
            // 其它（ass/ssa/txt 等）没有候选名会走到这里，无需处理
            else -> ""
        }
    }.getOrNull().orEmpty()

    /** 单个音频的导出结果：字幕处理结果 + 是否另外写出了 lrc 文件。 */
    private class AudioExportResult(val subtitle: SubtitleResult, val wroteLrc: Boolean)

    /**
     * 导出音频：有对应字幕就写同名 lrc 并内嵌歌词，否则原样复制。
     */
    private fun exportAudio(src: File, targetDir: DocumentFile, audioTitle: String, subtitle: File?): AudioExportResult {
        val sub = subtitle?.takeIf { it.exists() }
        if (sub == null) {
            writeFile(targetDir, sanitize(audioTitle), src)
            return AudioExportResult(SubtitleResult.NONE, false)
        }
        val lrc = lyricsFromSubtitle(sub)
        if (lrc.isBlank()) {
            writeFile(targetDir, sanitize(audioTitle), src)
            return AudioExportResult(SubtitleResult.FAILED, false)
        }
        val temp = File(context.cacheDir, "export_" + System.nanoTime() + "_" + sanitize(audioTitle))
        src.copyTo(temp, overwrite = true)
        return try {
            val embedded = embedLyrics(temp, lrc)
            // 无论内嵌是否成功，同名 lrc 都写到音频同级（跨目录找到的字幕也因此"落到"音频旁边）
            writeText(targetDir, lrcFileName(audioTitle), lrc)
            writeFile(targetDir, sanitize(audioTitle), temp)
            AudioExportResult(if (embedded) SubtitleResult.EMBEDDED else SubtitleResult.FAILED, true)

        } finally {
            runCatching { temp.delete() }
        }
    }

    /**
     * 用 jaudiotagger 将歌词文本写入音频元数据（mp3=USLT、flac/ogg=LYRICS、m4a=©lyr、wav=ID3 chunk）。
     *
     * @return 是否写入成功
     */
    private fun embedLyrics(file: File, text: String): Boolean = runCatching {
        // jaudiotagger 只对这些容器有 reader；opus / aac 会直接抛
        // "No Reader associated with this extension"（实测确认），判定为未内嵌。
        // lrc 文件仍会写出，播放器照常能用。
        if (file.extension.lowercase() !in EMBEDDABLE_EXTENSIONS) return false

        val af = AudioFileIO.read(file)
        // 关键修复：原先写的是 `val tag = af.tag ?: return@runCatching` ——
        // **完全没有标签块的 MP3**（这类文件很常见）getTag() 返回 null，于是直接放弃：
        // lrc 照写、内嵌却悄悄丢了。这里改成没有标签就创建默认标签再写。
        val tag = af.tag ?: af.createDefaultTag().also { af.setTag(it) }
        tag.setField(FieldKey.LYRICS, text)
        af.commit()
        true
    }.getOrDefault(false)

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
