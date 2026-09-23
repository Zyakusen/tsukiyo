package io.github.zyakusen.tsukiyo.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.zyakusen.tsukiyo.data.entity.DownloadItem
import io.github.zyakusen.tsukiyo.player.PlayableTrack
import io.github.zyakusen.tsukiyo.player.PlayerManager
import io.github.zyakusen.tsukiyo.ui.LocalContainer
import io.github.zyakusen.tsukiyo.ui.components.CoverImage
import io.github.zyakusen.tsukiyo.ui.components.EmptyState
import io.github.zyakusen.tsukiyo.ui.components.TopBar
import io.github.zyakusen.tsukiyo.util.formatSize
import java.io.File
import kotlinx.coroutines.launch

private fun baseName(title: String): String {
    val t = title.substringAfterLast('/').substringAfterLast('\\')
    val i = t.lastIndexOf('.')
    return if (i > 0) t.substring(0, i) else t
}

private fun isSubtitleFile(title: String): Boolean {
    val t = title.lowercase()
    return t.endsWith(".lrc") || t.endsWith(".vtt") || t.endsWith(".srt") || t.endsWith(".ass") || t.endsWith(".ssa") || t.endsWith(".txt")
}

private fun isAudioFile(title: String): Boolean {
    val t = title.lowercase()
    return t.endsWith(".wav") || t.endsWith(".mp3") || t.endsWith(".m4a") || t.endsWith(".aac") || t.endsWith(".flac") || t.endsWith(".ogg") || t.endsWith(".opus")
}

@Composable
fun DownloadsScreen(navController: NavHostController) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads by container.downloadManager.observeAll().collectAsState(initial = emptyList())
    var selectedWork by remember { mutableStateOf<Long?>(null) }

    fun play(item: DownloadItem) {
        val path = item.localPath ?: return
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
            return
        }
        val base = baseName(item.title)
        val subtitleFile = downloads
            .filter { it.workId == item.workId && it.id != item.id && isSubtitleFile(it.title) && baseName(it.title) == base }
            .mapNotNull { it.localPath?.let(::File) }
            .firstOrNull { it.exists() }
        val track = PlayableTrack(
            id = item.id,
            title = item.title,
            highUrl = Uri.fromFile(file).toString(),
            lowUrl = null,
            workTitle = item.workTitle,
            workId = item.workId,
            workCode = item.workCode,
            coverUrl = item.coverUrl,
            durationMs = item.duration,
            subtitleUrl = subtitleFile?.let { Uri.fromFile(it).toString() }
        )
        PlayerManager.playQueue(context, listOf(track), 0, "high")
        navController.navigate(io.github.zyakusen.tsukiyo.ui.navigation.Routes.PLAYER)
    }

    val selected = selectedWork
    BackHandler(enabled = selected != null) {
        selectedWork = null
    }
    if (selected == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("我的下载")
            if (downloads.isEmpty()) {
                EmptyState("暂无下载")
            } else {
                val groups = downloads.groupBy { it.workId }
                LazyColumn(Modifier.fillMaxSize()) {
                    groups.forEach { (workId, items) ->
                        item(key = workId) {
                            val first = items.firstOrNull()
                            Row(
                                Modifier.fillMaxWidth().clickable { selectedWork = workId }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CoverImage(first?.coverUrl, Modifier.size(52.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        first?.workTitle ?: "",
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "${items.size} 个文件",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    } else {
        val items = downloads.filter { it.workId == selected }
        val workTitle = items.firstOrNull()?.workTitle ?: ""
        Column(Modifier.fillMaxSize()) {
            TopBar(workTitle, onBack = { selectedWork = null })
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            when (item.status) {
                                DownloadItem.STATUS_DONE -> Text(
                                    "已下载 · ${formatSize(item.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                DownloadItem.STATUS_DOWNLOADING -> {
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { item.progress },
                                        modifier = Modifier.fillMaxWidth().height(4.dp)
                                    )
                                    Text("下载中 ${(item.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DownloadItem.STATUS_FAILED -> Text("下载失败", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                else -> Text("排队中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        when (item.status) {
                            DownloadItem.STATUS_DONE -> {
                                if (isAudioFile(item.title)) {
                                    IconButton(onClick = { play(item) }) { Icon(Icons.Filled.PlayArrow, "播放") }
                                }
                            }
                            DownloadItem.STATUS_FAILED -> {
                                IconButton(onClick = { scope.launch { container.downloadManager.retry(item) } }) { Icon(Icons.Filled.Refresh, "重试") }
                            }
                        }
                        IconButton(onClick = { scope.launch { container.downloadManager.delete(item) } }) {
                            Icon(Icons.Filled.Delete, "删除")
                        }
                    }
                }
            }
        }
    }
}
