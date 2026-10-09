package io.github.zyakusen.tsukiyo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.zyakusen.tsukiyo.ui.LocalContainer
import io.github.zyakusen.tsukiyo.ui.components.FilterActionDialog
import io.github.zyakusen.tsukiyo.ui.components.FilterPickerDialog
import io.github.zyakusen.tsukiyo.ui.components.TagChip
import io.github.zyakusen.tsukiyo.ui.components.TopBar
import io.github.zyakusen.tsukiyo.ui.navigation.navigateToSearch
import io.github.zyakusen.tsukiyo.util.FilterType
import io.github.zyakusen.tsukiyo.util.SearchFilter
import io.github.zyakusen.tsukiyo.util.SearchPreset

/**
 * 标签收藏页：标签 / 社团 / 声优 三段。
 *
 * 交互与作品详情页的标签一致：
 *  * **点击** → 清空搜索页现有条件，只按这一项筛选（`SearchPreset(clearExisting = true)`）；
 *  * **长按** → 选择正选 / 反选（这里**不提供收藏开关**，收藏的增删在「＋」里做）；
 *  * 每段标题左侧的 **＋** → 打开与搜索页同款的选择弹窗（顶部有搜索栏），点一行即加入/移出收藏。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagFavoritesScreen(navController: NavHostController) {
    val container = LocalContainer.current
    val favorites by container.tagFavorites.state.collectAsState()

    var pickerType by remember { mutableStateOf<FilterType?>(null) }
    var action by remember { mutableStateOf<Pair<FilterType, String>?>(null) }

    fun apply(type: FilterType, name: String, exclude: Boolean) {
        SearchPreset.pending = SearchPreset.Pending(SearchFilter(type, name, name, exclude), true)
        navController.navigateToSearch()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("标签收藏", onBack = { navController.popBackStack() })

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "点击标签＝只按它筛选；长按＝选正选或反选；标题左侧的 ＋ 用来增删收藏。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                FavoriteSection(
                    title = "标签",
                    names = favorites.tags,
                    emptyHint = "还没有收藏的标签",
                    onAdd = { pickerType = FilterType.TAG },
                    onClick = { name -> apply(FilterType.TAG, name, false) },
                    onLongClick = { name -> action = FilterType.TAG to name }
                )
            }
            item {
                FavoriteSection(
                    title = "社团",
                    names = favorites.circles,
                    emptyHint = "还没有收藏的社团",
                    onAdd = { pickerType = FilterType.CIRCLE },
                    onClick = { name -> apply(FilterType.CIRCLE, name, false) },
                    onLongClick = { name -> action = FilterType.CIRCLE to name }
                )
            }
            item {
                FavoriteSection(
                    title = "声优",
                    names = favorites.vas,
                    emptyHint = "还没有收藏的声优",
                    onAdd = { pickerType = FilterType.VA },
                    onClick = { name -> apply(FilterType.VA, name, false) },
                    onLongClick = { name -> action = FilterType.VA to name }
                )
            }
        }
    }

    pickerType?.let { type ->
        FilterPickerDialog(
            type = type,
            container = container,
            favoritesMode = true,
            favorites = favorites,
            onToggleFavorite = { name -> container.tagFavorites.toggle(type, name) },
            onDismiss = { pickerType = null }
        )
    }

    action?.let { (type, name) ->
        FilterActionDialog(
            title = name,
            // 收藏页里的长按：多一个「取消收藏」（这里一定已收藏，所以恒显示"取消收藏"）
            isFavorite = true,
            onToggleFavorite = {
                action = null
                container.tagFavorites.toggle(type, name)
            },
            onDismiss = { action = null },
            onInclude = {
                action = null
                apply(type, name, false)
            },
            onExclude = {
                action = null
                apply(type, name, true)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FavoriteSection(
    title: String,
    names: List<String>,
    emptyHint: String,
    onAdd: () -> Unit,
    onClick: (String) -> Unit,
    onLongClick: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 加号放在标题左旁
            IconButton(onClick = onAdd, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Add, "添加$title", modifier = Modifier.size(20.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(8.dp))
            Text(
                "${names.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (names.isEmpty()) {
            Text(
                emptyHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp)
            ) {
                names.forEach { name ->
                    TagChip(
                        text = name,
                        onClick = { onClick(name) },
                        onLongClick = { onLongClick(name) }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
