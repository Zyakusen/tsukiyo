package io.github.zyakusen.tsukiyo.util

import io.github.zyakusen.tsukiyo.data.model.Tag

data class ProgressOption(val value: String, val label: String)

val progressOptions = listOf(
    ProgressOption("marked", "想听"),
    ProgressOption("listening", "在听"),
    ProgressOption("listened", "已听"),
    ProgressOption("replay", "重听"),
    ProgressOption("postponed", "搁置")
)

val tagLanguageOptions = listOf(
    "zh-cn" to "中文",
    "ja-jp" to "日本語",
    "en-us" to "English"
)

fun progressLabel(value: String?): String =
    progressOptions.firstOrNull { it.value == value }?.label ?: (value ?: "")

fun displayPlaylistName(name: String?): String = when (name) {
    "__SYS_PLAYLIST_MARKED" -> "我标记的"
    "__SYS_PLAYLIST_LIKED" -> "我喜欢的"
    else -> name ?: "播放列表"
}

fun isSystemPlaylist(name: String?): Boolean = name?.startsWith("__SYS_PLAYLIST_") == true

/** 从播放列表链接或纯 id 中解析出 uuid（如 https://www.asmr.one/playlist?id=xxx 或 .../playlist/xxx）。 */
fun parsePlaylistId(input: String): String? {
    val trimmed = input.trim()
    Regex("[?&]id=([0-9a-fA-F]{8}-[0-9a-fA-F-]+)").find(trimmed)?.groupValues?.get(1)?.let { return it }
    Regex("playlist/([0-9a-fA-F]{8}-[0-9a-fA-F-]+)").find(trimmed)?.groupValues?.get(1)?.let { return it }
    return Regex("^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$").find(trimmed)?.groupValues?.get(1)
}

data class PrivacyOption(val value: Int, val label: String)

val privacyOptions = listOf(
    PrivacyOption(0, "私享"),
    PrivacyOption(1, "不公开"),
    PrivacyOption(2, "公开")
)

fun privacyLabel(value: Int?): String = privacyOptions.firstOrNull { it.value == value }?.label ?: "私享"

fun tagDisplayName(tag: Tag, lang: String): String =
    tag.i18n?.get(lang)?.name?.takeIf { it.isNotBlank() } ?: tag.name ?: ""

/** 低愿力标签：网站已做分类，voteStatus==0 即低愿力（筛选不采纳、网页上显示为灰色）。 */
fun isLowVoteTag(tag: Tag): Boolean = tag.voteStatus == 0
