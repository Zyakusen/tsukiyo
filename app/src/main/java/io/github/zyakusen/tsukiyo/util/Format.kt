package io.github.zyakusen.tsukiyo.util

import java.util.Locale

fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.ROOT, "%d:%02d", m, sec)
}

fun formatMs(ms: Long): String = formatDuration(ms / 1000)

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    return String.format(Locale.ROOT, "%.1f %s", value, units[i])
}

fun formatCount(n: Int?): String {
    if (n == null) return "0"
    return when {
        n >= 10000 -> String.format(Locale.ROOT, "%.1f万", n / 10000.0)
        else -> n.toString()
    }
}
