package io.github.zyakusen.tsukiyo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 极简 Markdown 渲染（**零依赖**，只覆盖发行说明里常用的语法）。
 *
 * 背景：GitHub Release 的说明是 release 对象上的一个 Markdown **文本字段**（`/releases/latest` 的 `body`），
 * 不是文件；网页端会渲染它，而 App 之前是直接 `Text(body)`，所以用户看到的是 `**加粗**`、`- 列表` 这类源码。
 *
 * 支持：`#`–`######` 标题、`-`/`*`/`+` 无序列表、`1.` 有序列表、`>` 引用、`---` 分隔线，
 * 行内 `**粗体**`、`*斜体*`/`_斜体_`、`` `代码` ``、`[文字](链接)`（只做样式，点击交给弹窗里的按钮）。
 * 不支持的语法按普通文字显示，不会报错。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = MaterialTheme.typography.bodySmall
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inline(block.text, codeBg, linkColor),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.bodyMedium
                    },
                    fontWeight = FontWeight.Bold
                )

                is MdBlock.Bullet -> Row(verticalAlignment = Alignment.Top) {
                    Text("•  ", style = baseStyle, color = subtle)
                    Text(inline(block.text, codeBg, linkColor), style = baseStyle)
                }

                is MdBlock.Ordered -> Row(verticalAlignment = Alignment.Top) {
                    Text("${block.number}.  ", style = baseStyle, color = subtle)
                    Text(inline(block.text, codeBg, linkColor), style = baseStyle)
                }

                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(subtle.copy(alpha = 0.6f))
                    )
                    Text(
                        inline(block.text, codeBg, linkColor),
                        style = baseStyle,
                        fontStyle = FontStyle.Italic,
                        color = subtle,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                MdBlock.Rule -> HorizontalDivider()

                is MdBlock.Table -> TableBlock(block, baseStyle, codeBg, linkColor)

                is MdBlock.Paragraph -> Text(inline(block.text, codeBg, linkColor), style = baseStyle)
            }
        }
    }
}

/** 块级元素。 */
internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Ordered(val number: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Rule : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data class Paragraph(val text: String) : MdBlock
}

/**
 * 表格：按各列内容长度分配宽度（长列占比大，但不让某一列吃掉全部），单元格里的行内语法照样渲染。
 * 窄屏上文字自动换行，不会溢出。
 */
@Composable
private fun TableBlock(
    table: MdBlock.Table,
    baseStyle: TextStyle,
    codeBg: Color,
    linkColor: Color
) {
    val cols = table.header.size.coerceAtLeast(1)
    val weights = (0 until cols).map { i ->
        val maxLen = (listOf(table.header.getOrElse(i) { "" }) + table.rows.map { it.getOrElse(i) { "" } })
            .maxOfOrNull { it.length } ?: 1
        maxLen.coerceIn(3, 16).toFloat()
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            table.header.forEachIndexed { i, cell ->
                Text(
                    inline(cell, codeBg, linkColor),
                    style = baseStyle,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(weights.getOrElse(i) { 1f }).padding(end = 6.dp)
                )
            }
        }
        HorizontalDivider()
        table.rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                (0 until cols).forEach { i ->
                    Text(
                        inline(row.getOrElse(i) { "" }, codeBg, linkColor),
                        style = baseStyle,
                        modifier = Modifier.weight(weights.getOrElse(i) { 1f }).padding(end = 6.dp)
                    )
                }
            }
        }
    }
}

private fun isTableRow(line: String) = line.length > 1 && line.startsWith("|")

/** `| --- | :--: |` 这种分隔行。 */
private fun isTableSeparator(line: String) =
    isTableRow(line) && line.contains('-') &&
        line.replace("|", "").replace(" ", "").all { it == '-' || it == ':' }

/** 拆单元格：去掉首尾竖线后按 `|` 切分。 */
private fun tableCells(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

private val ORDERED = Regex("^(\\d{1,3})\\.\\s+(.*)$")
private val HEADING = Regex("^(#{1,6})\\s+(.*)$")

/**
 * 按行解析成块；连续的普通行合并成一段（Markdown 的软换行语义），空行只是分段。
 * 纯函数、不依赖 Compose，方便单独核对。
 */
internal fun parseMarkdown(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flush() {
        if (paragraph.isNotEmpty()) {
            out += MdBlock.Paragraph(paragraph.toString())
            paragraph.clear()
        }
    }

    val lines = src.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        when {
            line.isEmpty() -> {
                flush()
                i++
            }

            // 表格：连续若干行以 | 开头，第二行是 | --- | 分隔行
            isTableRow(line) -> {
                flush()
                val block = mutableListOf<String>()
                while (i < lines.size && isTableRow(lines[i].trim())) {
                    block += lines[i].trim()
                    i++
                }
                val header = tableCells(block.first())
                val bodyStart = if (block.size > 1 && isTableSeparator(block[1])) 2 else 1
                val rows = block.drop(bodyStart).map { tableCells(it) }
                out += MdBlock.Table(header, rows)
            }

            HEADING.matches(line) -> {
                flush()
                val m = HEADING.find(line)!!
                out += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim())
                i++
            }

            line == "---" || line == "***" || line == "___" -> {
                flush()
                out += MdBlock.Rule
                i++
            }

            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> {
                flush()
                out += MdBlock.Bullet(line.substring(2).trim())
                i++
            }

            ORDERED.matches(line) -> {
                flush()
                val m = ORDERED.find(line)!!
                out += MdBlock.Ordered(m.groupValues[1], m.groupValues[2].trim())
                i++
            }

            line.startsWith("> ") || line == ">" -> {
                flush()
                out += MdBlock.Quote(line.removePrefix(">").trim())
                i++
            }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
                i++
            }
        }
    }
    flush()
    return out
}

/** 行内样式：粗体 / 斜体 / 行内代码 / 链接文字（只上色，不做点击）。 */
internal fun inline(src: String, codeBg: Color, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < src.length) {
        when {
            src.startsWith("**", i) -> {
                val end = src.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(src.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append(src[i]); i++
                }
            }

            src[i] == '`' -> {
                val end = src.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                        append(src.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(src[i]); i++
                }
            }

            src[i] == '[' -> {
                val close = src.indexOf(']', i + 1)
                val open = if (close > 0) src.indexOf('(', close) else -1
                val end = if (open > 0) src.indexOf(')', open) else -1
                if (close > i && open == close + 1 && end > open) {
                    withStyle(SpanStyle(color = linkColor)) { append(src.substring(i + 1, close)) }
                    i = end + 1
                } else {
                    append(src[i]); i++
                }
            }

            src[i] == '*' || src[i] == '_' -> {
                val mark = src[i]
                val end = src.indexOf(mark, i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(src.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(src[i]); i++
                }
            }

            else -> {
                append(src[i]); i++
            }
        }
    }
}
