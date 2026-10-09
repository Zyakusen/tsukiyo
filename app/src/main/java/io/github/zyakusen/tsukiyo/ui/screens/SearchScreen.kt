package io.github.zyakusen.tsukiyo.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.github.zyakusen.tsukiyo.data.model.Work
import io.github.zyakusen.tsukiyo.ui.LocalContainer
import io.github.zyakusen.tsukiyo.ui.SearchViewModel
import io.github.zyakusen.tsukiyo.ui.components.EmptyState
import io.github.zyakusen.tsukiyo.ui.components.FilterPickerDialog
import io.github.zyakusen.tsukiyo.ui.components.PagedGrid
import io.github.zyakusen.tsukiyo.ui.components.TopBar
import io.github.zyakusen.tsukiyo.ui.components.WorkCard
import io.github.zyakusen.tsukiyo.ui.navigation.Routes
import io.github.zyakusen.tsukiyo.util.FilterType
import io.github.zyakusen.tsukiyo.util.PagingState
import io.github.zyakusen.tsukiyo.util.SearchFilter
import io.github.zyakusen.tsukiyo.util.SearchPreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavHostController) {
    val container = LocalContainer.current
    val settings by container.settingsStore.state.collectAsState()
    val activity = LocalContext.current as? ComponentActivity
    val vm: SearchViewModel = if (activity != null) {
        viewModel(viewModelStoreOwner = activity) { SearchViewModel(container) }
    } else {
        viewModel { SearchViewModel(container) }
    }

    var showPicker by remember { mutableStateOf<FilterType?>(null) }
    // 长按筛选 chip → 询问是否收藏（只对标签/社团/声优有效）
    var favoriteTarget by remember { mutableStateOf<SearchFilter?>(null) }
    val favorites by container.tagFavorites.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.applyPendingPreset()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "搜索",
            actions = {
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Filled.Refresh, "刷新")
                }
                // 标签收藏入口（放在刷新按钮右边）
                IconButton(onClick = { navController.navigate(Routes.TAG_FAVORITES) }) {
                    Icon(Icons.Filled.Star, "标签收藏")
                }
            }
        )

        OutlinedTextField(
            value = vm.keyword,
            onValueChange = { vm.updateKeyword(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            placeholder = { Text("搜索作品、标签、社团、声优…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (vm.keyword.isNotEmpty()) {
                    IconButton(onClick = { vm.clearSearch() }) { Icon(Icons.Filled.Close, "清除") }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large
        )

        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { vm.commitSearch() }) { Text("搜索") }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 12.dp),
                modifier = Modifier.weight(1f)
            ) {
                item { FilterAddChip("标签") { showPicker = FilterType.TAG } }
                item { FilterAddChip("社团") { showPicker = FilterType.CIRCLE } }
                item { FilterAddChip("声优") { showPicker = FilterType.VA } }
                item { FilterAddChip("时长") { showPicker = FilterType.DURATION } }
                item { FilterAddChip("评分") { showPicker = FilterType.RATE } }
                item { FilterAddChip("价格") { showPicker = FilterType.PRICE } }
                item { FilterAddChip("销量") { showPicker = FilterType.SELL } }
                item { FilterAddChip("年龄") { showPicker = FilterType.AGE } }
                item { FilterAddChip("语言") { showPicker = FilterType.LANG } }
            }
        }

        if (vm.filters.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(vm.filters.toList(), key = { it.raw }) { f ->
                    FilterChip(
                        filter = f,
                        onToggle = { vm.toggleFilter(f) },
                        onRemove = { vm.removeFilter(f) },
                        onLongClick = if (container.tagFavorites.canFavorite(f.type)) {
                            { favoriteTarget = f }
                        } else null
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 0.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(sortOptions) { option ->
                    SelectableChip(
                        label = option.label,
                        selected = vm.sortKey == option.key,
                        onClick = { vm.setSort(option.key) }
                    )
                }
            }
            IconButton(onClick = { vm.toggleSortAsc() }) {
                Icon(Icons.Filled.SwapVert, "排序方向",
                    tint = if (vm.sortAsc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (vm.query.isBlank()) {
            EmptyState("输入关键词或添加筛选条件开始搜索")
        } else {
            PagedGrid(
                state = vm.paging,
                key = { it.id ?: 0L },
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
            ) { work ->
                WorkCard(work, blurNsfw = !settings.showNsfw) { work.id?.let { navController.navigate(Routes.work(it)) } }
            }
        }
    }

    showPicker?.let { type ->
        FilterPickerDialog(
            type = type,
            onDismiss = { showPicker = null },
            onPick = { filter ->
                vm.addFilter(filter)
                showPicker = null
            },
            container = container
        )
    }

    favoriteTarget?.let { f ->
        val isFav = favorites.contains(f.type, f.name)
        AlertDialog(
            onDismissRequest = { favoriteTarget = null },
            title = { Text(if (isFav) "取消收藏" else "收藏") },
            text = { Text("${if (isFav) "要把" else "把"}「${f.label}」${if (isFav) "移出收藏" else "加入收藏"}吗？") },
            confirmButton = {
                TextButton(onClick = {
                    container.tagFavorites.toggle(f.type, f.name)
                    favoriteTarget = null
                }) { Text(if (isFav) "取消收藏" else "收藏") }
            },
            dismissButton = { TextButton(onClick = { favoriteTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun FilterAddChip(label: String, onClick: () -> Unit) {
    SelectableChip(label = label, selected = false, onClick = onClick)
}

@Composable
private fun FilterChip(
    filter: SearchFilter,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (filter.isExclude) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                filter.display,
                style = MaterialTheme.typography.labelMedium,
                color = if (filter.isExclude) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .pointerInput(onToggle, onLongClick) {
                        detectTapGestures(
                            onTap = { onToggle() },
                            onLongPress = { onLongClick?.invoke() }
                        )
                    }
                    .padding(start = 12.dp, top = 3.dp, bottom = 3.dp)
            )
            Icon(
                Icons.Filled.Close,
                "移除",
                tint = if (filter.isExclude) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .size(14.dp)
            )
        }
    }
}

// FilterPickerDialog / FilterOptionRow 已移到 ui/components/FilterPickerDialog.kt
// （收藏页的「＋」也要用它，且新增了"收藏编辑"模式，所以做成公共组件避免两份实现）。
