package io.github.zyakusen.tsukiyo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
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

    var refreshFlag by remember { mutableStateOf(0) }
    var showPageDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // 首页顶部两行数据放在 ViewModel 中（跨导航保留），返回首帧列表项数量就与离开时一致，
    // 否则 rememberLazyGridState 按绝对下标恢复会让位置"错一个/两个作品"。
    LaunchedEffect(settings.showPopularAtHome, refreshFlag) {
        if (settings.showPopularAtHome) {
            vm.loadPopular(force = refreshFlag > 0)
        } else {
            vm.clearPopular()
        }
    }

    LaunchedEffect(settings.showRecommendAtHome, auth.isRealUser, refreshFlag) {
        vm.loadRecommend(
            enabled = settings.showRecommendAtHome && auth.isRealUser,
            force = refreshFlag > 0
        )
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
                IconButton(onClick = { vm.refreshCurrentPage(); refreshFlag++ }) {
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
        // 只有"页码真的变化"时才回到顶部。
        // 不能写成 LaunchedEffect(vm.currentPage) { if (currentPage > 1) scrollToItem(0) }：
        // LaunchedEffect 首次进入也会执行，从详情页返回第 N 页时会把好不容易恢复的位置直接重置到顶端。
        var lastPage by remember { mutableStateOf(vm.currentPage) }
        LaunchedEffect(vm.currentPage) {
            if (vm.currentPage != lastPage) {
                lastPage = vm.currentPage
                gridState.scrollToItem(0)
            }
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
                // 给顶部行加稳定 key：即使这两行出现/消失，Compose 也能按 key 重新锚定首个可见项，
                // 而不是退回"绝对下标"导致位置整体错位。
                if (settings.showPopularAtHome) {
                    item(key = "home-popular", span = { GridItemSpan(2) }) {
                        HorizontalWorkRow(
                            title = "热门作品",
                            works = vm.popularWorks,
                            blurNsfw = !settings.showNsfw,
                            loading = vm.popularLoading,
                            error = vm.popularError,
                            onRetry = { vm.loadPopular(force = true) },
                            onClick = { work -> work.id?.let { navController.navigate(Routes.work(it)) } }
                        )
                    }
                }

                if (settings.showRecommendAtHome && auth.isRealUser) {
                    item(key = "home-recommend", span = { GridItemSpan(2) }) {
                        HorizontalWorkRow(
                            title = "推荐作品",
                            works = vm.recommendWorks,
                            blurNsfw = !settings.showNsfw,
                            loading = vm.recommendLoading,
                            error = vm.recommendError,
                            onRetry = { vm.loadRecommend(enabled = true, force = true) },
                            onClick = { work -> work.id?.let { navController.navigate(Routes.work(it)) } }
                        )
                    }
                }
            }

            itemsIndexed(vm.items, key = { _, w -> w.id ?: 0L }) { _, work ->
                WorkCard(work, blurNsfw = !settings.showNsfw) { work.id?.let { navController.navigate(Routes.work(it)) } }
            }

            if (vm.loading && vm.items.isEmpty()) {
                item(key = "home-loading", span = { GridItemSpan(2) }) { LoadingIndicator() }
            }
            // 只要出错就提示，不再要求"列表为空"才显示：
            // 以前在第 N 页翻页失败时会静默停在旧列表、点重试还跳回第 1 页（见 BrowseViewModel.failedPage）。
            vm.error?.let { err ->
                item(key = "home-error", span = { GridItemSpan(2) }) {
                    ErrorState("第 ${vm.failedPage ?: vm.currentPage} 页加载失败：$err") { vm.retry() }
                }
            }

            if (vm.items.isNotEmpty() && vm.totalPages > 1) {
                item(key = "home-pager", span = { GridItemSpan(2) }) {
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

/**
 * 首页顶部的横向作品行（热门 / 推荐）。
 *
 * 关键点：**只要开关打开就一直占位**，不再"没数据就不显示"——
 * 以前两行后到会让下面的作品整体下移，用户要上滑才知道顶部加载好了。
 *  * 有数据（含上次缓存）→ 直接显示，请求回来替换；
 *  * 没数据且加载中 → 灰色骨架卡片占位（告诉用户"这里有东西"）；
 *  * 加载失败 → 一行可点击的「加载失败，点击重试」。
 */
@Composable
private fun HorizontalWorkRow(
    title: String,
    works: List<Work>,
    blurNsfw: Boolean,
    loading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    onClick: (Work) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            if (loading && works.isEmpty()) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            }
        }
        when {
            works.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(works, key = { it.id ?: 0L }) { work ->
                    Box(Modifier.width(128.dp)) {
                        WorkCard(work, blurNsfw = blurNsfw) { onClick(work) }
                    }
                }
            }

            loading -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { PlaceholderCard() }
            }

            error != null -> Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = onRetry != null) { onRetry?.invoke() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "加载失败，点击重试",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> Text(
                "暂无内容",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
}

/** 骨架卡片：尺寸尽量贴近 [WorkCard]，避免占位与真实内容高度差太多。 */
@Composable
private fun PlaceholderCard() {
    val grey = MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.width(128.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(grey)
        )
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.92f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(grey))
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(grey))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.45f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(grey))
    }
}
