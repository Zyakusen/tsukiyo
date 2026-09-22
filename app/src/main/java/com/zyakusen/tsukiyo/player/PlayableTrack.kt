package com.zyakusen.tsukiyo.player

/**
 * 可播放音轨（用于播放队列与 UI 展示）。
 */
data class PlayableTrack(
    val id: String,
    val title: String,
    val highUrl: String,
    val lowUrl: String?,
    val workTitle: String,
    val workId: Long,
    val workCode: String,
    val coverUrl: String?,
    val durationMs: Long,
    val subtitleUrl: String? = null
) {
    fun urlFor(quality: String): String =
        if (quality == "low") lowUrl?.takeIf { it.isNotBlank() } ?: highUrl else highUrl

    fun hasLowQuality(): Boolean = !lowUrl.isNullOrBlank()
}

/** 解析后的字幕行 */
data class LrcLine(val timeMs: Long, val text: String)
