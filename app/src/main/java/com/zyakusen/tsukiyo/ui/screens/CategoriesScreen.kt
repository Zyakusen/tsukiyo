package com.zyakusen.tsukiyo.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.zyakusen.tsukiyo.data.model.Work
import com.zyakusen.tsukiyo.ui.LocalContainer
import com.zyakusen.tsukiyo.ui.components.PagedGrid
import com.zyakusen.tsukiyo.ui.components.TopBar
import com.zyakusen.tsukiyo.ui.components.WorkCard
import com.zyakusen.tsukiyo.ui.navigation.Routes
import com.zyakusen.tsukiyo.util.PagingState

@Composable
fun TagWorksScreen(tagId: Long, navController: NavHostController) {
    val container = LocalContainer.current
    var tagName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tagId) {
        runCatching { tagName = container.repository.getTag(tagId).name }
    }
    FilterWorksScreen(
        title = tagName ?: "标签",
        filterQuery = tagName?.let { "\$tag:$it\$" },
        navController = navController
    )
}

@Composable
fun CircleWorksScreen(circleId: Long, navController: NavHostController) {
    val container = LocalContainer.current
    var name by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(circleId) {
        runCatching { name = container.repository.getCircle(circleId).name }
    }
    FilterWorksScreen(
        title = name ?: "社团",
        filterQuery = name?.let { "\$circle:$it\$" },
        navController = navController
    )
}

@Composable
fun VaWorksScreen(vaId: String, navController: NavHostController) {
    val container = LocalContainer.current
    var name by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vaId) {
        runCatching { name = container.repository.getVa(vaId).name }
    }
    FilterWorksScreen(
        title = name ?: "声优",
        filterQuery = name?.let { "\$va:$it\$" },
        navController = navController
    )
}

@Composable
private fun FilterWorksScreen(title: String, filterQuery: String?, navController: NavHostController) {
    val container = LocalContainer.current
    val settings by container.settingsStore.state.collectAsState()
    var sortKey by remember { mutableStateOf("release") }
    var sortAsc by remember { mutableStateOf(false) }

    val paging = remember(filterQuery, sortKey, sortAsc, settings.subtitlesOnly) {
        PagingState<Work> { page ->
            if (filterQuery == null) {
                (emptyList<Work>()) to 0
            } else {
                val resp = container.repository.search(filterQuery, sortKey, if (sortAsc) "asc" else "desc", page, 20)
                (resp.works ?: emptyList()) to (resp.pagination?.totalCount ?: Int.MAX_VALUE)
            }
        }
    }
    LaunchedEffect(paging) { paging.refresh() }

    Column(Modifier.fillMaxSize()) {
        TopBar(title, onBack = { navController.popBackStack() })
        Row(Modifier.padding(horizontal = 12.dp)) {
            LazyRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(sortOptions) { option ->
                    SelectableChip(option.label, sortKey == option.key) { sortKey = option.key }
                }
            }
        }
        PagedGrid(state = paging, key = { it.id ?: 0L }) { work ->
            WorkCard(work, blurNsfw = !settings.showNsfw) { work.id?.let { navController.navigate(Routes.work(it)) } }
        }
    }
}
