package com.zyakusen.tsukiyo.util

import com.zyakusen.tsukiyo.data.model.Track
import com.zyakusen.tsukiyo.data.model.Work
import com.zyakusen.tsukiyo.player.LrcLine
import com.zyakusen.tsukiyo.player.PlayableTrack

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

/** 解析 .lrc 字幕文本 */
fun parseLrc(content: String): List<LrcLine> {
    val lines = mutableListOf<LrcLine>()
    val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    for (raw in content.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        val matches = timeTag.findAll(line).toList()
        if (matches.isEmpty()) continue
        val text = line.substring(line.lastIndexOf(']') + 1).trim()
        for (m in matches) {
            val min = m.groupValues[1].toLongOrNull() ?: 0L
            val sec = m.groupValues[2].toLongOrNull() ?: 0L
            val frac = m.groupValues[3].let { s ->
                if (s.isEmpty()) 0L else (s.padEnd(3, '0').take(3).toLongOrNull() ?: 0L)
            }
            val timeMs = min * 60_000 + sec * 1000 + frac
            lines.add(LrcLine(timeMs, text))
        }
    }
    return lines.sortedBy { it.timeMs }
}

/** 解析 WebVTT 字幕文本（时间戳行与文本行分开）。 */
fun parseVtt(content: String): List<LrcLine> {
    val lines = mutableListOf<LrcLine>()
    val timeTag = Regex("""(\d{1,3}):(\d{2}):(\d{2})[.](\d{3})\s*-->""")
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

/** 根据播放位置查找当前字幕行索引 */
fun currentLrcIndex(lines: List<LrcLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    var idx = -1
    for (i in lines.indices) {
        if (lines[i].timeMs <= positionMs) idx = i else break
    }
    return idx
}

/**
 * 智能路径：根据用户偏好计算打开作品时默认进入的文件夹路径（从根到目标文件夹的 Track 链）。
 *
 * 逻辑：
 * 1. 深度优先遍历文件树，只考虑「直接包含音频文件」的文件夹。
 * 2. 若开启效果音偏好，优先进入「含 SE（且非 SEなし/无SE）」的文件夹，且优先匹配音频类型顺序。
 * 3. 再按音频类型偏好顺序依次查找。
 * 4. 都找不到时，兜底返回第一个直接包含音频的文件夹。
 */
fun findSmartPath(nodes: List<Track>, sePreference: Boolean, audioTypeOrder: List<String>): List<Track> {
    // SE 文件夹：标题含 SE/効果音/效果音/音效，但排除「なし/无/無」（表示无效果音）
    fun isSeFolder(folder: Track): Boolean {
        val t = folder.title?.lowercase() ?: return false
        if (t.contains("なし") || t.contains("无") || t.contains("無")) return false
        return t.contains("se") || t.contains("効果音") || t.contains("效果音") || t.contains("音效")
    }

    fun directlyContainsAudio(folder: Track): Boolean =
        folder.children?.any { it.type == "audio" } == true

    fun containsType(folder: Track, type: String): Boolean =
        folder.children?.any { it.type == "audio" && it.title?.lowercase()?.endsWith(".$type") == true } == true

    fun findFolder(predicate: (Track) -> Boolean): List<Track>? {
        fun walk(list: List<Track>, path: List<Track>): List<Track>? {
            for (n in list) {
                if (n.type != "folder") continue
                val newPath = path + n
                if (directlyContainsAudio(n) && predicate(n)) return newPath
                n.children?.let { child -> walk(child, newPath)?.let { return it } }
            }
            return null
        }
        return walk(nodes, emptyList())
    }

    if (sePreference) {
        // 优先：SE 且匹配音频类型顺序
        for (type in audioTypeOrder) {
            findFolder { isSeFolder(it) && containsType(it, type) }?.let { return it }
        }
        // 其次：SE（任意格式）
        findFolder { isSeFolder(it) }?.let { return it }
    }
    for (type in audioTypeOrder) {
        findFolder { containsType(it, type) }?.let { return it }
    }
    return findFolder { true } ?: emptyList()
}
