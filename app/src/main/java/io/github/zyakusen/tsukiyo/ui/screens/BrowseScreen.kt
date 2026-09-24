package io.github.zyakusen.tsukiyo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.github.zyakusen.tsukiyo.data.model.Work
import io.github.zyakusen.tsukiyo.ui.BrowseViewModel
import io.github.zyakusen.tsukiyo.ui.LocalContainer
import io.github.zyakusen.tsukiyo.ui.components.ErrorState
import io.github.zyakusen.tsukiyo.ui.components.LoadingIndicator
import io.github.zyakusen.tsukiyo.ui.components.TopBar
import io.github.zyakusen.tsukiyo.ui.components.WorkCard
import io.github.zyakusen.tsukiyo.ui.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun BrowseScreen(navController: NavHostController) {
    val container = LocalContainer.current
    val settings by container.settingsStore.state.collectAsState()
    val auth by container.authManager.state.collectAsState()
    val vm: BrowseViewModel = viewModel { BrowseViewModel(container) }

    var popularWorks by remember { mutableStateOf<List<Work>>(emptyList()) }
    var recommendWorks by remember { mutableStateOf<List<Work>>(emptyList()) }
    var refreshFlag by remember { mutableStateOf(0) }
    var showPageDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(settings.showPopularAtHome, refreshFlag) {
        if (settings.showPopularAtHome) {
            runCatching { popularWorks = container.repository.popularWorks(pageSize = 20).works ?: emptyList() }
        }
    }

    LaunchedEffect(settings.showRecommendAtHome, auth.isRealUser, refreshFlag) {
        if (settings.showRecommendAtHome && auth.isRealUser) {
            runCatching { recommendWorks = container.repository.recommendWorks(pageSize = 20).works ?: emptyList() }
        } else {
            recommendWorks = emptyList()
        }
    }

    LaunchedEffect(settings.subtitlesOnly) {
        if (vm.appliedSubtitlesOnly != settings.subtitlesOnly) {
            vm.appliedSubtitlesOnly = settings.subtitlesOnly
            vm.refresh()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "Tsukiyo",
            actions = {
                IconButton(onClick = { vm.refresh(); refreshFlag++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
                IconButton(onClick = { navController.navigate(Routes.HISTORY) }) {
                    Icon(Icons.Filled.History, contentDescription = "历史")
                }
                IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                }
            }
        )

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
                        selected = vm.sortKey == option.key && option.key != "betterRandom",
                        onClick = {
                            if (vm.sortKey != option.key) {
                                vm.setSort(option.key)
                                vm.refresh()
                            }
                        }
                    )
                }
            }
            IconButton(onClick = {
                vm.toggleSortAsc()
                vm.refresh()
            }) {
                Icon(Icons.Filled.SwapVert, "切换排序方向",
                    tint = if (vm.sortAsc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val gridState = rememberLazyGridState()
        val scrollSignal by container.homeScrollToTopSignal.collectAsState()
        LaunchedEffect(scrollSignal) {
            if (scrollSignal > 0) gridState.scrollToItem(0)
        }
        LaunchedEffect(vm.currentPage) {
            if (vm.currentPage > 1) gridState.scrollToItem(0)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (vm.currentPage == 1) {
                if (settings.showPopularAtHome && popularWorks.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        HorizontalWorkRow(
                            title = "热门作品",
                            works = popularWorks,
                            blurNsfw = !settings.showNsfw,
                            onClick = { work -> work.id?.let { navController.navigate(Routes.work(it)) } }
                        )
                    }
                }

                if (settings.showRecommendAtHome && auth.isRealUser && recommendWorks.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        HorizontalWorkRow(
                            title = "推荐作品",
                            works = recommendWorks,
                            blurNsfw = !settings.showNsfw,
                            onClick = { work -> work.id?.let { navController.navigate(Routes.work(it)) } }
                        )
                    }
                }
            }

            itemsIndexed(vm.items, key = { _, w -> w.id ?: 0L }) { _, work ->
                WorkCard(work, blurNsfw = !settings.showNsfw) { work.id?.let { navController.navigate(Routes.work(it)) } }
            }

            if (vm.loading && vm.items.isEmpty()) {
                item(span = { GridItemSpan(2) }) { LoadingIndicator() }
            }
            if (vm.error != null && vm.items.isEmpty()) {
                item(span = { GridItemSpan(2) }) { ErrorState(vm.error) { vm.refresh() } }
            }

            if (vm.items.isNotEmpty() && vm.totalPages > 1) {
                item(span = { GridItemSpan(2) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { vm.prevPage() }, enabled = vm.currentPage > 1 && !vm.loading) {
                            Text("上一页")
                        }
                        TextButton(onClick = { showPageDialog = true }) {
                            Text("${vm.currentPage} / ${vm.totalPages}", color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { vm.nextPage() }, enabled = vm.currentPage < vm.totalPages && !vm.loading) {
                            Text("下一页")
                        }
                    }
                }
            }
        }
    }

    if (showPageDialog) {
        PageJumpDialog(
            current = vm.currentPage,
            total = vm.totalPages,
            onDismiss = { showPageDialog = false },
            onJump = { page ->
                showPageDialog = false
                vm.goToPage(page)
            }
        )
    }
}

@Composable
private fun PageJumpDialog(current: Int, total: Int, onDismiss: () -> Unit, onJump: (Int) -> Unit) {
    var input by remember { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转到第几页") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { v -> input = v.filter { it.isDigit() } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                placeholder = { Text("1 - $total") }
            )
        },
        confirmButton = {
            TextButton(onClick = { input.toIntOrNull()?.let { onJump(it.coerceIn(1, total)) } }) { Text("跳转") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun HorizontalWorkRow(
    title: String,
    works: List<Work>,
    blurNsfw: Boolean,
    onClick: (Work) -> Unit
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(works, key = { it.id ?: 0L }) { work ->
                Box(Modifier.width(128.dp)) {
                    WorkCard(work, blurNsfw = blurNsfw) { onClick(work) }
                }
            }
        }
    }
}
