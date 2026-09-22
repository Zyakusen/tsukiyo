package com.zyakusen.tsukiyo.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.zyakusen.tsukiyo.data.model.Work
import com.zyakusen.tsukiyo.ui.BrowseViewModel
import com.zyakusen.tsukiyo.ui.LocalContainer
import com.zyakusen.tsukiyo.ui.components.ErrorState
import com.zyakusen.tsukiyo.ui.components.LoadingIndicator
import com.zyakusen.tsukiyo.ui.components.TopBar
import com.zyakusen.tsukiyo.ui.components.WorkCard
import com.zyakusen.tsukiyo.ui.navigation.Routes
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
            vm.paging.refresh()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "Tsukiyo",
            actions = {
                IconButton(onClick = { vm.refresh(); refreshFlag++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
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
                                scope.launch { vm.paging.refresh() }
                            }
                        }
                    )
                }
            }
            IconButton(onClick = {
                vm.toggleSortAsc()
                scope.launch { vm.paging.refresh() }
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

        val shouldLoadMore by remember {
            derivedStateOf {
                val info = gridState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                last >= info.totalItemsCount - 6
            }
        }
        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore && vm.paging.items.isNotEmpty()) {
                scope.launch { vm.paging.loadMore() }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
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

            itemsIndexed(vm.paging.items, key = { _, w -> w.id ?: 0L }) { _, work ->
                WorkCard(work, blurNsfw = !settings.showNsfw) { work.id?.let { navController.navigate(Routes.work(it)) } }
            }

            if (vm.paging.loading) {
                item(span = { GridItemSpan(2) }) { LoadingIndicator() }
            }
            if (vm.paging.error != null && vm.paging.items.isEmpty()) {
                item(span = { GridItemSpan(2) }) { ErrorState(vm.paging.error) { scope.launch { vm.paging.refresh() } } }
            }
        }
    }
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
