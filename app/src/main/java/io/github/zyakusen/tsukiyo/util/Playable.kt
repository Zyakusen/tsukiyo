package io.github.zyakusen.tsukiyo.util

import io.github.zyakusen.tsukiyo.data.model.Track
import io.github.zyakusen.tsukiyo.data.model.Work
import io.github.zyakusen.tsukiyo.player.LrcLine
import io.github.zyakusen.tsukiyo.player.PlayableTrack

fun Track.toPlayable(work: Work, subtitleUrl: String? = null): PlayableTrack = PlayableTrack(
    id = hash ?: title ?: "track",
    title = title ?: "",
    highUrl = mediaStreamUrl ?: "",
    lowUrl = streamLowQualityUrl,
    workTitle = work.title ?: "",
    workId = work.id ?: 0L,
    workCode = work.sourceId ?: "",
    coverUrl = work.coverUrl,
    durationMs = ((duration ?: 0.0) * 1000).toLong(),
    subtitleUrl = subtitleUrl
)

fun Track.extension(): String {
    val name = title ?: ""
    val ext = name.substringAfterLast('.', "")
    return if (ext.isNotBlank() && ext.length <= 5) ext.uppercase() else ""
}

fun isAudioFile(title: String): Boolean {
    val t = title.lowercase()
    return t.endsWith(".wav") || t.endsWith(".mp3") || t.endsWith(".m4a") || t.endsWith(".aac") ||
        t.endsWith(".flac") || t.endsWith(".ogg") || t.endsWith(".opus") || t.endsWith(".wma") ||
        t.endsWith(".mp4") || t.endsWith(".webm")
}

fun isVideoFile(title: String): Boolean {
    val t = title.lowercase()
    return t.endsWith(".mp4") || t.endsWith(".webm")
}

fun isSubtitleFile(title: String): Boolean {
    val t = title.lowercase()
    return t.endsWith(".lrc") || t.endsWith(".vtt") || t.endsWith(".srt") ||
        t.endsWith(".ass") || t.endsWith(".ssa") || t.endsWith(".txt")
}

fun isImageFile(title: String): Boolean {
    val t = title.lowercase()
    return t.endsWith(".jpg") || t.endsWith(".jpeg") || t.endsWith(".png") ||
        t.endsWith(".webp") || t.endsWith(".gif") || t.endsWith(".bmp")
}

/** 去除媒体/字幕扩展名，得到用于匹配字幕的基础名。 */
fun baseName(title: String): String {
    var name = title.trim()
    val exts = listOf(
        ".wav", ".mp3", ".m4a", ".aac", ".flac", ".ogg", ".opus", ".wma",
        ".lrc", ".vtt", ".srt", ".ass", ".ssa", ".txt", ".pdf", ".mp4", ".webm"
    )
    var changed = true
    while (changed) {
        changed = false
        for (e in exts) {
            if (name.lowercase().endsWith(e)) {
                name = name.dropLast(e.length)
                changed = true
                break
            }
        }
    }
    return name.trim()
}

/**
 * 音频对应的字幕候选文件名。
 *
 *  * `音频全名 + .vtt/.srt/.lrc`：服务器常见的「字幕紧跟在音频名后」命名（如 `01 xxx.mp3.vtt`，
 *    也有社团直接给 `01 xxx.mp3.lrc`）；
 *  * `去掉音频扩展名 + .lrc/.srt`：社团自压的字幕（如 `01 xxx.lrc`）。
 *
 * **播放与导出共用这一份规则**，避免两边匹配逻辑不一致。
 */
fun subtitleNamesFor(audioTitle: String): List<String> {
    val base = baseName(audioTitle)
    return listOf(audioTitle + ".vtt", audioTitle + ".srt", audioTitle + ".lrc", "$base.lrc", "$base.srt")
}

/** 解析 .lrc 字幕文本（支持 [mm:ss.cc] 与 [hh:mm:ss.cc] 两种时间戳）。 */
fun parseLrc(content: String): List<LrcLine> {
    val lines = mutableListOf<LrcLine>()
    val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?::(\d{1,2}))?(?:[.:](\d{1,3}))?]""")
    for (raw in content.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        val matches = timeTag.findAll(line).toList()
        if (matches.isEmpty()) continue
        val text = line.substring(line.lastIndexOf(']') + 1).trim()
        for (m in matches) {
            val g1 = m.groupValues[1].toLongOrNull() ?: 0L
            val g2 = m.groupValues[2].toLongOrNull() ?: 0L
            val g3 = m.groupValues[3].takeIf { it.isNotEmpty() }?.toLongOrNull()
            val frac = m.groupValues[4].let { s ->
                if (s.isEmpty()) 0L else (s.padEnd(3, '0').take(3).toLongOrNull() ?: 0L)
            }
            val timeMs = if (g3 != null) {
                g1 * 3_600_000 + g2 * 60_000 + g3 * 1000 + frac
            } else {
                g1 * 60_000 + g2 * 1000 + frac
            }
            lines.add(LrcLine(timeMs, text))
        }
    }
    return lines.sortedBy { it.timeMs }
}

/** 解析 WebVTT 字幕文本（时间戳行与文本行分开）。SRT 的毫秒分隔符是逗号，一并兼容。 */
fun parseVtt(content: String): List<LrcLine> {
    val lines = mutableListOf<LrcLine>()
    val timeTag = Regex("""(\d{1,3}):(\d{2}):(\d{2})[.,](\d{3})\s*-->""")
    var startMs: Long? = null
    for (raw in content.lineSequence()) {
        val line = raw.trim()
        val m = timeTag.find(line)
        when {
            m != null -> {
                val h = m.groupValues[1].toLongOrNull() ?: 0L
                val min = m.groupValues[2].toLongOrNull() ?: 0L
                val sec = m.groupValues[3].toLongOrNull() ?: 0L
                val ms = m.groupValues[4].toLongOrNull() ?: 0L
                startMs = h * 3_600_000 + min * 60_000 + sec * 1000 + ms
            }
            line.isEmpty() || line.startsWith("WEBVTT") || line.startsWith("NOTE") || line.startsWith("STYLE") -> startMs = null
            startMs != null -> {
                lines.add(LrcLine(startMs, line))
                startMs = null
            }
        }
    }
    return lines.sortedBy { it.timeMs }
}

/** 根据内容自动识别 LRC / WebVTT 并解析。 */
fun parseSubtitle(content: String): List<LrcLine> {
    val trimmed = content.trimStart()
    return if (trimmed.startsWith("WEBVTT") || trimmed.contains("-->")) parseVtt(content) else parseLrc(content)
}

/** 将 WebVTT / SRT 文本转换为 LRC 文本（时间戳换算为 [mm:ss.xx]，多行文本合并为一行）。 */
fun vttToLrc(content: String): String {
    // SRT 的时间戳用逗号分隔毫秒（00:00:01,000），这里与点号一起兼容
    val timeTag = Regex("""(\d{2}):(\d{2}):(\d{2})[.,](\d{3})\s*-->""")
    val out = StringBuilder()
    var cueStart: Long? = null
    val cueText = StringBuilder()

    fun flush() {
        val start = cueStart ?: return
        if (cueText.isNotBlank()) {
            val cs = start / 10
            val min = cs / 6000
            val sec = (cs % 6000) / 100
            val c = cs % 100
            val text = cueText.toString().trim().replace(Regex("\\s*\n\\s*"), " ")
            out.append("[%02d:%02d.%02d]%s\n".format(min, sec, c, text))
        }
        cueStart = null
        cueText.clear()
    }

    for (raw in content.lineSequence()) {
        val line = raw.trim()
        val m = timeTag.find(line)
        when {
            m != null -> {
                flush()
                cueStart = m.groupValues[1].toLongOrNull()?.times(3_600_000)
                    ?.plus((m.groupValues[2].toLongOrNull() ?: 0L) * 60_000)
                    ?.plus((m.groupValues[3].toLongOrNull() ?: 0L) * 1000)
                    ?.plus(m.groupValues[4].toLongOrNull() ?: 0L)
            }
            line.isEmpty() -> flush()
            cueStart != null && !line.startsWith("WEBVTT") && !line.startsWith("NOTE") &&
                !line.startsWith("STYLE") && !line.startsWith("Kind:") && !line.startsWith("Language:") -> {
                if (cueText.isNotEmpty()) cueText.append('\n')
                cueText.append(line)
            }
        }
    }
    flush()
    return out.toString().trimEnd()
}

/** 根据播放位置查找当前字幕行索引 */
fun currentLrcIndex(lines: List<LrcLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    var idx = -1
    for (i in lines.indices) {
        if (lines[i].timeMs <= positionMs) idx = i else break
    }
    return idx
}

// 智能路径（findSmartPath）已迁移到 util/SmartPath.kt：
//   SeNames（命名规则：段/文件/非正片）+ SmartPath（候选收集、分阶段过滤、确定性 tie-break）
//   + PathKey（可校验寻址键）。
// 旧的 isSeFolder + DFS 先序「第一个命中即返回」实现已删除。
