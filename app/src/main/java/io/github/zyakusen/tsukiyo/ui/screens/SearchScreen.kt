package io.github.zyakusen.tsukiyo.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.github.zyakusen.tsukiyo.data.model.Circle
import io.github.zyakusen.tsukiyo.data.model.Tag
import io.github.zyakusen.tsukiyo.data.model.Va
import io.github.zyakusen.tsukiyo.data.model.Work
import io.github.zyakusen.tsukiyo.ui.LocalContainer
import io.github.zyakusen.tsukiyo.ui.SearchViewModel
import io.github.zyakusen.tsukiyo.ui.components.EmptyState
import io.github.zyakusen.tsukiyo.ui.components.PagedGrid
import io.github.zyakusen.tsukiyo.ui.components.TopBar
import io.github.zyakusen.tsukiyo.ui.components.WorkCard
import io.github.zyakusen.tsukiyo.ui.navigation.Routes
import io.github.zyakusen.tsukiyo.util.FilterType
import io.github.zyakusen.tsukiyo.util.PagingState
import io.github.zyakusen.tsukiyo.util.SearchFilter
import io.github.zyakusen.tsukiyo.util.SearchPreset
import io.github.zyakusen.tsukiyo.util.tagDisplayName

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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                        onRemove = { vm.removeFilter(f) }
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
                contentPadding = PaddingValues(vertical = 4.dp),
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
            PagedGrid(state = vm.paging, key = { it.id ?: 0L }) { work ->
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
}

@Composable
private fun FilterAddChip(label: String, onClick: () -> Unit) {
    SelectableChip(label = label, selected = false, onClick = onClick)
}

@Composable
private fun FilterChip(filter: SearchFilter, onToggle: () -> Unit, onRemove: () -> Unit) {
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
                    .clickable(onClick = onToggle)
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, "移除", modifier = Modifier.padding(0.dp))
            }
        }
    }
}

@Composable
private fun FilterPickerDialog(
    type: FilterType,
    onDismiss: () -> Unit,
    onPick: (SearchFilter) -> Unit,
    container: io.github.zyakusen.tsukiyo.data.AppContainer
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
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
                                        FilterOptionRow(name) { onPick(SearchFilter(FilterType.TAG, name, name)) }
                                    }
                                    FilterType.CIRCLE -> items(circles.filter { searchText.isBlank() || (it.name?.contains(searchText, true) == true) }) { c ->
                                        val name = c.name ?: return@items
                                        FilterOptionRow(name) { onPick(SearchFilter(FilterType.CIRCLE, name, name)) }
                                    }
                                    FilterType.VA -> items(vas.filter { searchText.isBlank() || (it.name?.contains(searchText, true) == true) }) { v ->
                                        val name = v.name ?: return@items
                                        FilterOptionRow(name) { onPick(SearchFilter(FilterType.VA, name, name)) }
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
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun FilterOptionRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
    )
}
