package io.github.zyakusen.tsukiyo.util

import io.github.zyakusen.tsukiyo.data.model.Track

/** 单段目录名的 SE 语义。 */
enum class SegmentSe { NEG, WITH, ONLY, NONE }

/** 音频文件名（主干）的 SE 语义。 */
enum class FileSe { NEG, WITH, NONE }

/** 候选目录的 SE 语义（见方案文档 §1.2）。 */
enum class SeClass { WITH, WITH_DEFAULT, MIXED, WITHOUT, ONLY }

/**
 * 「智能路径」的命名判定规则。
 *
 * 核心前提：**正常音声默认就是含效果音的**，因此未标注一律视为含 SE（WITH_DEFAULT），
 * 真正需要识别的只有三件事：显式无 SE、纯音效素材、非正片。
 */
object SeNames {

    /**
     * 词/段之间的连接符：半角与全角空格、`_ - ・ · /`，以及 `& ＆`。
     * （实测修订 §10-⑭：`SE＆BGMなし` 这种全角连接曾被漏判成含 SE）
     */
    private const val SEP = "[-_・·/ 　&＆]?"

    /** 显式无 SE：SEなし / 効果音なし / SE無し / noSE / non_SE / SE BGM無し / OFF SE / SE＆BGMなし / 声のみ */
    private val NEG = Regex(
        "(?:无|無|no|non|ノー|without|off)\\s*[-_・·/ ]?\\s*(?:se|sfx|bgm|効果音|效果音|音效)" +
            "|(?:se|効果音|效果音|音效|sfx)\\s*" + SEP +
            "\\s*(?:bgm|se|音|効果音)?\\s*" + SEP +
            "\\s*(?:なし|無し|オフ|off|カット|抜き|抜|less|无|無)" +
            "|(?:声|ボイス|セリフ|台詞|voice)\\s*(?:のみ|だけ)" +
            "|効果音\\s*(?:オフ|off)"
    )

    /** 显式含 SE：SEあり / SE有り / SE有 / 含SE / 带SE / SE付き / 効果音あり / with SE */
    private val WITH = Regex(
        "(?:se|効果音|效果音|音效|sfx)\\s*[-_・·/ ]?\\s*(?:あり|有り|有|付き|つき|入り|込み|on)" +
            "|(?:含|有|带|帶|with)\\s*[-_・·/ ]?\\s*(?:se|効果音|效果音|音效|sfx)"
    )

    /** 纯音效素材（整段就是 SE / 効果音 / SFX [素材|集|のみ|だけ|用|音源]）。 */
    private val ONLY = Regex("^(?:se|効果音|效果音|音效|sfx)(?:\\s*(?:素材|集|のみ|だけ|用|音源))*$")

    /** 通用 SE 词（词边界，避免 Presents / Sensei / SEX），以及全角 ＳＥ。 */
    private val TOKEN = Regex("(?<![a-z0-9])se(?![a-z0-9])|ｓｅ")

    /** 非正片：特典/附赠/促销/试听/闹钟/留言/感言/番外等（硬剔除，不看设置）。 */
    private val NON_MAIN = Regex(
        "特典|おまけ|オマケ|お負け|omake|bonus|extra|限定|exclusive|無料|无料|附赠|附贈|贈品|赠品|福利|礼包|" +
            "サンプル|sample|体験版|体验版|體驗版|试用版|試用版|试玩|試玩|お試し|試聴|试听|preview|" +
            "デモ|demo|予告|预告|告知|お知らせ|宣伝|紹介|プロモ|促销|宣传|预览|彩蛋|" +
            "スペシャル|special|" +
            "目覚まし|アラーム|alarm|時報|时报|起床|メッセージ|運営|(?<![a-z])message(?![a-z])|" +
            "フリートーク|キャストコメント|コメント|感言|感想|あとがき|クレジット|謝辞|お礼|挨拶|" +
            "インタビュー|メイキング|花絮|访谈|番外|特別編|スタッフ|" +
            "(?<![a-z])(?:promo|pr|cm|pv|sp|ex)(?![a-z0-9])"
    )

    /** 正片标记（本編 / 本篇 / 正編 / main）。 */
    private val MAIN_NAME = Regex("本編|本编|本篇|正編|正篇|(?<![a-z0-9])main(?![a-z0-9])")

    fun normalize(raw: String?): String = MediaTypes.normalize(raw)

    /** 去掉扩展名，得到文件名主干。 */
    fun stem(raw: String?): String {
        val n = normalize(raw)
        val i = n.lastIndexOf('.')
        return if (i > 0) n.substring(0, i) else n
    }

    /**
     * NEG 与 WITH 同时命中时的裁决：**谁在名字里先出现谁算数**（同位则 NEG 优先）。
     *
     * 例：`有音效无射精音` → WITH 在位置 0（有音效）、NEG 在位置 1（音效+无），语义是"有音效"，判 WITH。
     */
    private fun negWithConflict(n: String): SegmentSe? {
        val mn = NEG.find(n)
        val mw = WITH.find(n)
        if (mn != null && mw != null) {
            return if (mw.range.first < mn.range.first) SegmentSe.WITH else SegmentSe.NEG
        }
        return null
    }

    /** 文件夹名（段）分类。 */
    fun classifySegment(raw: String?): SegmentSe {
        val n = normalize(raw)
        if (n.isEmpty()) return SegmentSe.NONE
        negWithConflict(n)?.let { return it }
        if (NEG.containsMatchIn(n)) return SegmentSe.NEG
        if (ONLY.matches(n)) return SegmentSe.ONLY
        if (WITH.containsMatchIn(n)) return SegmentSe.WITH
        if (TOKEN.containsMatchIn(n)) return SegmentSe.WITH
        return SegmentSe.NONE
    }

    /** 文件名（主干）分类：只用 NEG / WITH（不用 ONLY，避免把 `SE.wav` 当成素材目录）。 */
    fun classifyFile(raw: String?): FileSe {
        val n = stem(raw)
        if (n.isEmpty()) return FileSe.NONE
        negWithConflict(n)?.let { return if (it == SegmentSe.WITH) FileSe.WITH else FileSe.NEG }
        if (NEG.containsMatchIn(n)) return FileSe.NEG
        if (WITH.containsMatchIn(n)) return FileSe.WITH
        if (TOKEN.containsMatchIn(n)) return FileSe.WITH
        return FileSe.NONE
    }

    /**
     * 单段目录名的"非正片"裁决（逐段判定，不跨层级）。
     *
     * 同段里既有正片词又有非正片词时，沿用"谁先出现谁算数"：
     *   `本編＋おまけ`            → 本編 在前 → 不是非正片（正片 + 附赠打包目录）
     *   `購入特典『本編SE無ver』`   → 特典 在前 → 是非正片（附赠里装了正片版本）
     */
    fun isSegmentNonMain(raw: String?): Boolean {
        val n = normalize(raw)
        if (n.isEmpty()) return false
        val mn = NON_MAIN.find(n) ?: return false
        val mm = MAIN_NAME.find(n)
        return mm == null || mm.range.first >= mn.range.first
    }

    /** 该名称本身是否命中非正片词表（供文件层"全部文件都是非正片"判定使用）。 */
    fun isNonMainName(raw: String?): Boolean = NON_MAIN.containsMatchIn(normalize(raw))

    fun isMainName(raw: String?): Boolean = MAIN_NAME.containsMatchIn(normalize(raw))

    /**
     * 整条路径（根 → 叶）的 SE 语义。
     *
     * 规则：任一段为 NEG → WITHOUT；任一段为 ONLY（且无 NEG 兄弟）→ ONLY；
     * 任一段为 WITH → WITH；否则 WITH_DEFAULT（默认含 SE）。
     * ONLY 段若存在 NEG 兄弟（`SE` 与 `non_SE` 成对），升级为 WITH。
     */
    fun classifyPath(path: List<Track>, siblingsOf: (Track) -> List<Track>): SeClass {
        val segs = path.map { classifySegment(it.title) }
        if (segs.any { it == SegmentSe.NEG }) return SeClass.WITHOUT
        if (segs.any { it == SegmentSe.ONLY }) {
            for (i in path.indices) {
                if (segs[i] != SegmentSe.ONLY) continue
                val self = path[i]
                val siblings = siblingsOf(self)
                if (siblings.any { it !== self && classifySegment(it.title) == SegmentSe.NEG }) {
                    return SeClass.WITH
                }
            }
            return SeClass.ONLY
        }
        if (segs.any { it == SegmentSe.WITH }) return SeClass.WITH
        return SeClass.WITH_DEFAULT
    }
}
