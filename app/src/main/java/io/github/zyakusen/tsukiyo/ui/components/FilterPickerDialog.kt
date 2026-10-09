package io.github.zyakusen.tsukiyo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zyakusen.tsukiyo.data.AppContainer
import io.github.zyakusen.tsukiyo.data.TagFavorites
import io.github.zyakusen.tsukiyo.data.model.Circle
import io.github.zyakusen.tsukiyo.data.model.Tag
import io.github.zyakusen.tsukiyo.data.model.Va
import io.github.zyakusen.tsukiyo.util.FilterType
import io.github.zyakusen.tsukiyo.util.SearchFilter
import io.github.zyakusen.tsukiyo.util.tagDisplayName

/**
 * 筛选条件选择弹窗（标签 / 社团 / 声优 / 时长 / 评分 …）。
 *
 * 两种模式：
 *  * **普通模式**（默认）：点一项就回调 [onPick]，由调用方立即作为筛选条件应用。
 *  * **[favoritesMode]**：用于往收藏里增删 —— 行首显示星标表示已收藏，点击**切换收藏并保持弹窗打开**
 *    （标题变成「收藏标签/社团/声优」，右下角是「完成」）。
 */
@Composable
fun FilterPickerDialog(
    type: FilterType,
    container: AppContainer,
    onDismiss: () -> Unit,
    onPick: (SearchFilter) -> Unit = {},
    favoritesMode: Boolean = false,
    favorites: TagFavorites = TagFavorites(),
    onToggleFavorite: (String) -> Unit = {}
) {
    var searchText by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var circles by remember { mutableStateOf<List<Circle>>(emptyList()) }
    var vas by remember { mutableStateOf<List<Va>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    val tagLanguage = container.settingsStore.tagLanguage

    LaunchedEffect(type, refreshKey) {
        loaded = false
        when (type) {
            FilterType.TAG -> tags = container.repository.getTags(force = refreshKey > 0)
            FilterType.CIRCLE -> circles = container.repository.getCircles(force = refreshKey > 0)
            FilterType.VA -> vas = container.repository.getVas(force = refreshKey > 0)
            else -> {}
        }
        loaded = true
    }

    val durationOptions = listOf("30min" to "时长 30 分钟", "1h" to "时长 1 小时", "2h" to "时长 2 小时", "3h" to "时长 3 小时")
    val rateOptions = listOf("4.5" to "评分 ≥ 4.5", "4.75" to "评分 ≥ 4.75", "5" to "评分 5.0")
    val priceOptions = listOf("100" to "价格 ≥ 100", "300" to "价格 ≥ 300", "500" to "价格 ≥ 500", "700" to "价格 ≥ 700", "1000" to "价格 ≥ 1000", "2000" to "价格 ≥ 2000")
    val sellOptions = listOf("100" to "销量 ≥ 100", "300" to "销量 ≥ 300", "500" to "销量 ≥ 500", "700" to "销量 ≥ 700", "1000" to "销量 ≥ 1000", "2000" to "销量 ≥ 2000")
    val ageOptions = listOf("general" to "全年龄", "r15" to "R-15", "adult" to "R-18")
    val langOptions = listOf("JPN" to "日语", "ENG" to "英语", "CHI_HANS" to "简体中文", "CHI_HANT" to "繁体中文", "KO_KR" to "韩语")

    // 收藏模式下点一行 = 切换收藏；普通模式 = 作为筛选条件返回
    fun onRow(name: String) {
        if (favoritesMode) onToggleFavorite(name) else onPick(SearchFilter(type, name, name))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (favoritesMode) {
                        when (type) {
                            FilterType.CIRCLE -> "收藏社团"
                            FilterType.VA -> "收藏声优"
                            else -> "收藏标签"
                        }
                    } else {
                        when (type) {
                            FilterType.TAG -> "选择标签"
                            FilterType.CIRCLE -> "选择社团"
                            FilterType.VA -> "选择声优"
                            FilterType.DURATION -> "选择时长"
                            FilterType.RATE -> "选择最低评分"
                            FilterType.PRICE -> "选择价格"
                            FilterType.SELL -> "选择销量"
                            FilterType.AGE -> "选择年龄分级"
                            FilterType.LANG -> "选择语言"
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                if (type == FilterType.TAG || type == FilterType.CIRCLE || type == FilterType.VA) {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Filled.Refresh, "刷新")
                    }
                }
            }
        },
        text = {
            when (type) {
                FilterType.DURATION -> {
                    LazyColumn {
                        items(durationOptions) { (v, label) ->
                            FilterOptionRow(label) { onPick(SearchFilter(FilterType.DURATION, v, label)) }
                        }
                    }
                }
                FilterType.RATE -> {
                    LazyColumn {
                        items(rateOptions) { (v, label) ->
                            FilterOptionRow(label) { onPick(SearchFilter(FilterType.RATE, v, label)) }
                        }
                    }
                }
                FilterType.PRICE -> {
                    LazyColumn {
                        items(priceOptions) { (v, label) ->
                            FilterOptionRow(label) { onPick(SearchFilter(FilterType.PRICE, v, label)) }
                        }
                    }
                }
                FilterType.SELL -> {
                    LazyColumn {
                        items(sellOptions) { (v, label) ->
                            FilterOptionRow(label) { onPick(SearchFilter(FilterType.SELL, v, label)) }
                        }
                    }
                }
                FilterType.AGE -> {
                    LazyColumn {
                        items(ageOptions) { (v, label) ->
                            FilterOptionRow(label) { onPick(SearchFilter(FilterType.AGE, v, label)) }
                        }
                    }
                }
                FilterType.LANG -> {
                    LazyColumn {
                        items(langOptions) { (v, label) ->
                            FilterOptionRow(label) { onPick(SearchFilter(FilterType.LANG, v, label)) }
                        }
                    }
                }
                else -> {
                    Column {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = { Text("筛选") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!loaded) {
                            Text("加载中…", modifier = Modifier.padding(16.dp))
                        } else {
                            LazyColumn(Modifier.padding(top = 8.dp)) {
                                when (type) {
                                    FilterType.TAG -> items(tags.filter { t -> searchText.isBlank() || tagDisplayName(t, tagLanguage).contains(searchText, true) }) { t ->
                                        val name = tagDisplayName(t, tagLanguage)
                                        FilterOptionRow(
                                            label = name,
                                            favorited = if (favoritesMode) favorites.contains(FilterType.TAG, name) else null,
                                            onClick = { onRow(name) }
                                        )
                                    }
                                    FilterType.CIRCLE -> items(circles.filter { searchText.isBlank() || (it.name?.contains(searchText, true) == true) }) { c ->
                                        val name = c.name ?: return@items
                                        FilterOptionRow(
                                            label = name,
                                            favorited = if (favoritesMode) favorites.contains(FilterType.CIRCLE, name) else null,
                                            onClick = { onRow(name) }
                                        )
                                    }
                                    FilterType.VA -> items(vas.filter { searchText.isBlank() || (it.name?.contains(searchText, true) == true) }) { v ->
                                        val name = v.name ?: return@items
                                        FilterOptionRow(
                                            label = name,
                                            favorited = if (favoritesMode) favorites.contains(FilterType.VA, name) else null,
                                            onClick = { onRow(name) }
                                        )
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (favoritesMode) "完成" else "取消") }
        }
    )
}

/**
 * 列表里的一行。[favorited] 非空时（收藏模式）在行首显示星标：实心=已收藏。
 */
@Composable
private fun FilterOptionRow(label: String, favorited: Boolean? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (favorited != null) {
            Icon(
                if (favorited) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (favorited) "已收藏" else "未收藏",
                tint = if (favorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
