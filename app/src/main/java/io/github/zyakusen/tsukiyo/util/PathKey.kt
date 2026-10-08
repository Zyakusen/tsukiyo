package io.github.zyakusen.tsukiyo.util

import io.github.zyakusen.tsukiyo.data.model.Track

/**
 * 智能路径的寻址键：每段编码为「同级序号 + 标题」，解码时任一段定位失败即返回 null，
 * 由调用方回退到重新计算智能路径（不再像旧实现那样静默截断到上层）。
 *
 * 旧的实现只存标题，导致：同名目录走错分支、目录改名后静默截断、空标题目录退化为根目录。
 */
object PathKey {

    private const val SEG = "\u0001"
    private const val SUB = "\u0002"

    /** 明确选择"停在根目录"（音频就在根目录）的哨兵值。 */
    const val ROOT_KEY = "\u0000root"

    /** 编码：每段 = "同级序号␂标题"。 */
    fun encode(nodes: List<Track>, path: List<Track>): String {
        if (path.isEmpty()) return ROOT_KEY
        val sb = StringBuilder()
        var level: List<Track> = nodes
        for (t in path) {
            val idx = level.indexOfFirst { it.type == "folder" && it === t }
            if (idx < 0) return ""
            if (sb.isNotEmpty()) sb.append(SEG)
            sb.append(idx).append(SUB).append(t.title ?: "")
            level = t.children ?: emptyList()
        }
        return sb.toString()
    }

    /** 解码：任一段定位失败返回 null（不再静默截断）。 */
    fun decode(nodes: List<Track>, key: String?): List<Track>? {
        if (key == null) return null
        if (key == ROOT_KEY) return emptyList()
        if (key.isEmpty()) return null
        var level: List<Track> = nodes
        val out = mutableListOf<Track>()
        for (seg in key.split(SEG)) {
            val i = seg.indexOf(SUB)
            val idx = if (i >= 0) seg.substring(0, i).toIntOrNull() else null
            val title = if (i >= 0) seg.substring(i + 1) else ""
            val byIdx = idx?.let { level.getOrNull(it) }
                ?.takeIf { it.type == "folder" && (it.title ?: "") == title }
            val folder = byIdx
                ?: level.firstOrNull { it.type == "folder" && (it.title ?: "") == title }
            if (folder == null) return null
            out += folder
            level = folder.children ?: emptyList()
        }
        return out
    }
}
