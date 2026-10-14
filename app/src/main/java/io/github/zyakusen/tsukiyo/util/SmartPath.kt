package io.github.zyakusen.tsukiyo.util

import io.github.zyakusen.tsukiyo.data.model.Track
import java.util.IdentityHashMap

/** 效果音偏好模式（替代原来的布尔开关 sePreference）。 */
enum class SeMode(val key: String) {
    WITH("with"),
    WITHOUT("without"),
    ANY("any");

    companion object {
        fun fromKey(key: String?): SeMode = when (key) {
            WITHOUT.key -> WITHOUT
            ANY.key -> ANY
            else -> WITH
        }
    }
}

data class SmartPathPrefs(
    val seMode: SeMode = SeMode.WITH,
    val audioTypeOrder: List<String> = emptyList()
)

/** 智能路径结果：path 为空表表示"停在根目录"，reason 便于排查（如 drop-noSE|drop-nonmain|fmt=wav）。 */
data class SmartPathResult(val path: List<Track>, val reason: String)

/** 候选目录：直接含音频文件的目录；根目录候选的 path 为空表。 */
data class DirCandidate(
    val path: List<Track>,
    val se: SeClass,
    val seByPath: SeClass,
    val formats: List<String>,
    val files: List<String>,
    val audioCount: Int,
    val depth: Int,
    val order: Int,
    val isMain: Boolean,
    val isNonMain: Boolean,
    /**
     * 只统计**正片文件**的扩展名（文件名未命中非正片词表的那批）。
     *
     * 用途：格式判定不该被"这个目录里放了几个赠品 mp3"带偏 ——
     * 例：RJ01588205 根目录是 WAV 正篇 + 5 个【赠品】mp3，以前根目录会被当成"mp3 目录"，
     * 于是与真正的 mp3 目录（简体中文）在格式阶段打平，最后靠 depth（根目录最浅）赢下，
     * 结果用户进了一个混装的大目录。改用正片文件的扩展名后，根目录是 wav、简体中文是 mp3，
     * 在「mp3 优先」下就会正确进入简体中文。
     */
    val mainFormats: List<String> = emptyList()
) {
    val label: String
        get() = if (path.isEmpty()) "(ROOT)" else path.joinToString("/") { it.title ?: "" }

    /** 格式判定的取值来源：优先用正片文件，一个正片文件都没有时退回全部文件。 */
    val formatSource: List<String>
        get() = mainFormats.ifEmpty { formats }
}

private val DEFAULT_ORDER = listOf("flac", "wav", "opus", "m4a", "aac", "mp3")

/** 音质提示（次级证据：仅在扩展名判不出或打平时使用）。 */
private val HINT_LOSSLESS = Regex("高音質|高音质|ロスレス|無損|无损|ハイレゾ|hi-?res|lossless|高品質|hq|wav|flac")
private val HINT_NORMAL = Regex("通常|標準|标准|ノーマル|normal|スタンダード|mp3")

private val QUALITY_MARK = Regex("高音質|高音质|ロスレス|無損|无损|ハイレゾ|lossless|通常|標準|标准|ノーマル")

private val SE_MARK = Regex(
    "(?:无|無|no|non|ノー|without|off)\\s*[-_・·/ ]?\\s*(?:se|効果音|效果音|音效|sfx)" +
        "|(?:se|効果音|效果音|音效|sfx)\\s*[-_・·/ 　&＆]?\\s*" +
        "(?:あり|有り|有|付き|つき|入り|込み|on|なし|無し|オフ|off|カット|抜き|抜)" +
        "|(?:含|有|带|帶|with)\\s*[-_・·/ ]?\\s*(?:se|効果音|效果音|音效|sfx)" +
        "|(?<![a-z0-9])se(?![a-z0-9])"
)

/**
 * 收集候选目录（一次 DFS）：
 *  * 直接含音频文件的每个目录；
 *  * **根目录本身也是候选**（音频可能直接散在根目录，与 イラスト 等混放）。
 *
 * SE 语义分两层：先看目录名（路径级），目录名"没表态"时再用**文件名**补判
 * （全部文件为无 SE → WITHOUT；部分为无 SE → MIXED）。
 */
fun collectAudioDirs(nodes: List<Track>): List<DirCandidate> {
    val out = mutableListOf<DirCandidate>()
    val siblingsOf = IdentityHashMap<Track, List<Track>>()

    fun isAudioNode(t: Track): Boolean =
        t.type == "audio" || (t.type != "folder" && MediaTypes.isAudio(t.title))

    fun add(path: List<Track>, direct: List<Track>) {
        val byPath = SeNames.classifyPath(path) { siblingsOf[it] ?: nodes }
        val fileClasses = direct.map { SeNames.classifyFile(it.title) }
        val se = if (byPath != SeClass.WITH_DEFAULT) {
            byPath
        } else when {
            fileClasses.isNotEmpty() && fileClasses.all { it == FileSe.NEG } -> SeClass.WITHOUT
            fileClasses.any { it == FileSe.NEG } -> SeClass.MIXED
            else -> SeClass.WITH_DEFAULT
        }
        out += DirCandidate(
            path = path,
            se = se,
            seByPath = byPath,
            formats = direct.mapNotNull { MediaTypes.extOf(it.title).ifEmpty { null } },
            files = direct.mapNotNull { it.title },
            audioCount = direct.size,
            depth = path.size,
            order = out.size,
            isMain = path.any { SeNames.isMainName(it.title) },
            // 目录名逐段裁决；或"目录内所有音频文件名都是非正片" → 整体视为非正片
            isNonMain = path.any { SeNames.isSegmentNonMain(it.title) } ||
                (direct.isNotEmpty() && direct.all { SeNames.isNonMainName(it.title) }),
            // F1：格式判定只看正片文件（见 mainFormats 的注释）
            mainFormats = direct.filterNot { SeNames.isNonMainName(it.title) }
                .mapNotNull { MediaTypes.extOf(it.title).ifEmpty { null } }
        )
    }

    // 根目录候选
    val rootDirect = nodes.filter { isAudioNode(it) }
    if (rootDirect.isNotEmpty()) add(emptyList(), rootDirect)

    fun walk(list: List<Track>, path: List<Track>) {
        for (n in list) {
            if (n.type != "folder") continue
            val children = n.children ?: emptyList()
            for (c in children) siblingsOf[c] = children
            val np = path + n
            val direct = children.filter { isAudioNode(it) }
            if (direct.isNotEmpty()) add(np, direct)
            walk(children, np)
        }
    }
    walk(nodes, emptyList())
    return out
}

/** 目录名的音质提示：无损类 → 0，通常类 → 1；无提示 → null。 */
private fun hintTier(name: String?): Int? {
    val n = SeNames.normalize(name)
    if (HINT_LOSSLESS.containsMatchIn(n)) return 0
    if (HINT_NORMAL.containsMatchIn(n)) return 1
    return null
}

/** 格式优先级：主证据=目录内**正片文件**的扩展名（见 DirCandidate.mainFormats）；判不出才用目录名音质提示。 */
private fun formatRank(c: DirCandidate, order: List<String>): Int? {
    val ranks = c.formatSource.mapNotNull { order.indexOf(it).takeIf { i -> i >= 0 } }
    if (ranks.isNotEmpty()) return ranks.min()
    for (seg in c.path.asReversed()) {
        when (hintTier(seg.title)) {
            0 -> {
                val fam = order.indices.filter { order[it] in MediaTypes.LOSSLESS }
                return fam.minOrNull() ?: 0
            }
            1 -> {
                val fam = order.indices.filter { order[it] in MediaTypes.LOSSY }
                return fam.minOrNull() ?: (order.size - 1)
            }
        }
    }
    return null
}

private fun qualityTier(c: DirCandidate, order: List<String>): Int {
    for (seg in c.path.asReversed()) {
        hintTier(seg.title)?.let { return it }
    }
    return 0
}

/**
 * 允许集合内，优先"更贴合用户意图"的那一类（**只在并列时起作用**）。
 *
 * 不限(ANY)：正常目录优先于"明确标注无 SE"的目录。否则并列会落到字典序（⑤ 键），
 *   出现「日文 `SE無し` 胜出、中文 `wav` 胜出」这种与语言/字形相关的差异
 *   （实测 4 份样本里修正 52 处，全部是"无 SE 目录 → 正常目录"）。
 * 优先无SE(WITHOUT)：纯无 SE 目录优先于混装(MIXED)。
 * 含SE(WITH)：无意义（无 SE 已被阶段 1 过滤掉），恒为 0。
 */
private fun seTieBreak(c: DirCandidate, mode: SeMode): Int = when (mode) {
    SeMode.ANY -> if (c.se == SeClass.WITHOUT) 1 else 0
    SeMode.WITHOUT -> if (c.se == SeClass.MIXED) 1 else 0
    SeMode.WITH -> 0
}

/** 与服务器返回顺序无关的稳定键：整条路径标题的字典序（NFKC + 小写）。 */
private fun pathTitleKey(c: DirCandidate): String =
    c.path.joinToString("/") { SeNames.normalize(it.title) }

/**
 * 智能路径主算法（方案文档 §4.3）：
 *   阶段 1 SE 过滤 → 阶段 2 剔除纯素材 → 阶段 3 剔除非正片
 *   → 阶段 4 格式排序 → 阶段 5 确定性 tie-break
 */
fun findSmartPath(nodes: List<Track>, prefs: SmartPathPrefs): SmartPathResult {
    val order = prefs.audioTypeOrder.map { it.lowercase() }.ifEmpty { DEFAULT_ORDER }
    val candidates = collectAudioDirs(nodes)
    if (candidates.isEmpty()) return SmartPathResult(emptyList(), "no-candidate")

    var pool = candidates
    val why = mutableListOf<String>()

    // 阶段 1：非正片（硬规则；全被剔空时回退）
    // **本篇保障优先于一切**：先确保候选都在正片范围内，再谈 SE 与格式。
    // 否则 SE 过滤可能把唯一的正片候选剔掉、只剩特典可回退 —— 违背"无论如何要进本篇"。
    val withoutNonMain = pool.filter { !it.isNonMain }
    if (withoutNonMain.isNotEmpty()) {
        pool = withoutNonMain
        why += "drop-nonmain"
    } else {
        why += "nonmain-fallback"
    }

    // 阶段 2：纯音效素材（硬规则）
    val withoutOnly = pool.filter { it.se != SeClass.ONLY }
    if (withoutOnly.isNotEmpty()) {
        pool = withoutOnly
        why += "drop-SE-only"
    }

    // 阶段 3：SE 语义过滤 —— 在正片集合内判断。
    // SE 的重要性高于音频格式：即使无 SE 目录里的格式优先级更高，也要先按 SE 过滤。
    when (prefs.seMode) {
        SeMode.WITH -> {
            val f = pool.filter { it.se != SeClass.WITHOUT }
            if (f.isNotEmpty()) {
                pool = f
                why += "drop-noSE"
            } else {
                why += "se-fallback"
            }
        }
        SeMode.WITHOUT -> {
            val f = pool.filter { it.se == SeClass.WITHOUT || it.se == SeClass.MIXED }
            if (f.isNotEmpty()) {
                pool = f
                why += "only-noSE"
            } else {
                why += "se-fallback"
            }
        }
        SeMode.ANY -> Unit
    }

    // 阶段 4：格式（只在通过前面所有过滤的集合内比格式优先级）
    val ranked = pool.map { it to formatRank(it, order) }
    val known = ranked.filter { it.second != null }
    val use = if (known.isNotEmpty()) known else ranked
    val best = use.mapNotNull { it.second }.minOrNull()
    val finalists = if (best == null) use else use.filter { it.second == best }
    if (best != null && best < order.size) why += "fmt=" + order[best]

    // 阶段 5：确定性 tie-break（键序经 405+ 真实作品实测调参）
    fun countBest(c: DirCandidate): Int =
        if (best == null) c.audioCount else c.formats.count { order.indexOf(it) == best }

    val winner = finalists.minWithOrNull(
        compareBy<Pair<DirCandidate, Int?>> { if (it.first.isMain) 0 else 1 }  // ① 正片标记
            .thenBy { it.first.depth }                                        // ② 层级浅者优先
            .thenBy { -countBest(it.first) }                                  // ③ 同层内最优格式文件多者
            .thenBy { qualityTier(it.first, order) }                          // ④ 音质提示
            .thenBy { seTieBreak(it.first, prefs.seMode) }                    // ⑤ 允许集合内的 SE 取向
            .thenBy { pathTitleKey(it.first) }                                // ⑥ 路径标题字典序（与顺序无关）
            .thenBy { it.first.order }                                        // ⑦ DFS 先序（兜底）
    )!!.first

    return SmartPathResult(winner.path, why.joinToString("|"))
}

/** 内容键：`01_SEあり.wav` 与 `01_SEなし.wav` → 同一个键 "01"。 */
fun contentKey(title: String): String {
    var n = SeNames.stem(title)
    n = SE_MARK.replace(n, "")
    n = QUALITY_MARK.replace(n, "")
    return Regex("[-_ ]+").replace(n, " ").trim()
}

/**
 * 同一目录内的内容层择优：
 * 按 seMode 滤掉不需要的版本，再按内容键去重，**组内保留格式优先级最高的那份**。
 */
fun selectContent(files: List<String>, mode: SeMode, order: List<String> = DEFAULT_ORDER): List<String> {
    val keep = when (mode) {
        SeMode.WITH -> files.filter { SeNames.classifyFile(it) != FileSe.NEG }
        SeMode.WITHOUT -> files.filter { SeNames.classifyFile(it) != FileSe.WITH }
        SeMode.ANY -> return files.toList()
    }
    val best = LinkedHashMap<String, Pair<Int, String>>()
    for (f in keep) {
        val k = contentKey(f)
        val r = order.indexOf(MediaTypes.extOf(f)).takeIf { it >= 0 } ?: order.size
        val cur = best[k]
        if (cur == null || r < cur.first) best[k] = r to f
    }
    return best.values.map { it.second }
}
