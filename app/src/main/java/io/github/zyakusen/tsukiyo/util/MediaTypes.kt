package io.github.zyakusen.tsukiyo.util

import java.text.Normalizer
import java.util.Locale

/**
 * 智能路径使用的媒体类型与扩展名工具。
 *
 * 刻意与 Playable.kt 里既有的 isAudioFile / isVideoFile / isSubtitleFile / isImageFile 并存、互不影响：
 * 那几个函数被下载导出与字幕匹配使用，改动它们会波及下载与字幕配对行为，
 * 因此本次「智能路径」改造只新增本文件作为新逻辑的单一事实来源。
 */
object MediaTypes {

    private val WS = Regex("\\s+")

    /** 智能路径认可的音频扩展名（无损 + 有损）。 */
    val AUDIO: Set<String> = setOf(
        "flac", "wav", "opus", "m4a", "aac", "mp3", "ogg", "wma",
        "aiff", "aif", "tak", "dsf", "alac"
    )

    val LOSSLESS: Set<String> = setOf("flac", "wav", "aiff", "aif", "tak", "dsf", "alac")

    val LOSSY: Set<String> = setOf("mp3", "aac", "opus", "ogg", "wma", "m4a")

    /** NFKC 归一化 → 去首尾空白 → 小写 → 空白折叠。 */
    fun normalize(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val n = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT)
        return WS.replace(n, " ")
    }

    /** 取扩展名（小写、1..5 位字母数字）；取不到返回空串。 */
    fun extOf(title: String?): String {
        val n = normalize(title)
        val i = n.lastIndexOf('.')
        if (i <= 0 || i == n.length - 1) return ""
        val e = n.substring(i + 1)
        return if (e.length in 1..5 && e.all { it.isLetterOrDigit() }) e else ""
    }

    fun isAudio(title: String?): Boolean = extOf(title) in AUDIO
}
