package io.github.zyakusen.tsukiyo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.zyakusen.tsukiyo.data.entity.HistoryItem
import io.github.zyakusen.tsukiyo.ui.LocalContainer
import io.github.zyakusen.tsukiyo.ui.components.CoverImage
import io.github.zyakusen.tsukiyo.ui.components.EmptyState
import io.github.zyakusen.tsukiyo.ui.components.TopBar
import io.github.zyakusen.tsukiyo.ui.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(navController: NavHostController) {
    val container = LocalContainer.current
    val settings by container.settingsStore.state.collectAsState()
    val history by container.historyStore.observe().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            "历史",
            onBack = { navController.popBackStack() },
            actions = {
                if (history.isNotEmpty()) {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Filled.Delete, "清空")
                    }
                }
            }
        )
        if (history.isEmpty()) {
            EmptyState("暂无浏览记录")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history, key = { it.workId }) { item ->
                    HistoryCard(item, blurNsfw = !settings.showNsfw) {
                        navController.navigate(Routes.work(item.workId))
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空历史") },
            text = { Text("确定要清空全部历史记录吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch { container.historyStore.clear() }
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun HistoryCard(item: HistoryItem, blurNsfw: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        CoverImage(
            url = item.coverUrl,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentDescription = item.title,
            blur = blurNsfw && item.nsfw
        )
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 2
        )
        if (item.circleName.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                item.circleName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
