package com.zyakusen.tsukiyo.util

import com.zyakusen.tsukiyo.data.model.Tag

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

data class PrivacyOption(val value: Int, val label: String)

val privacyOptions = listOf(
    PrivacyOption(0, "私享"),
    PrivacyOption(1, "不公开"),
    PrivacyOption(2, "公开")
)

fun privacyLabel(value: Int?): String = privacyOptions.firstOrNull { it.value == value }?.label ?: "私享"

fun tagDisplayName(tag: Tag, lang: String): String =
    tag.i18n?.get(lang)?.name?.takeIf { it.isNotBlank() } ?: tag.name ?: ""
