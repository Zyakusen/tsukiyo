package io.github.zyakusen.tsukiyo.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import io.github.zyakusen.tsukiyo.data.entity.DownloadItem
import io.github.zyakusen.tsukiyo.download.ExportOptions
import io.github.zyakusen.tsukiyo.player.PlayableTrack
import io.github.zyakusen.tsukiyo.player.PlayerManager
import io.github.zyakusen.tsukiyo.ui.LocalContainer
import io.github.zyakusen.tsukiyo.ui.components.CoverImage
import io.github.zyakusen.tsukiyo.ui.components.EmptyState
import io.github.zyakusen.tsukiyo.ui.components.TopBar
import io.github.zyakusen.tsukiyo.util.baseName
import io.github.zyakusen.tsukiyo.util.formatSize
import io.github.zyakusen.tsukiyo.util.isAudioFile
import io.github.zyakusen.tsukiyo.util.isImageFile
import io.github.zyakusen.tsukiyo.util.isSubtitleFile
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(navController: NavHostController) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads by container.downloadManager.observeAll().collectAsState(initial = emptyList())
    var selectedWork by rememberSaveable { mutableStateOf<Long?>(null) }
    var previewImage by remember { mutableStateOf<String?>(null) }
    var previewText by remember { mutableStateOf<String?>(null) }
    var deleteWorkId by remember { mutableStateOf<Long?>(null) }
    var exportWorkId by remember { mutableStateOf<Long?>(null) }
    var exportItem by remember { mutableStateOf<DownloadItem?>(null) }

    fun exportUri(): String? {
        val uri = container.settingsStore.exportDirUri
        if (uri.isBlank()) {
            Toast.makeText(context, "请先在设置中设置导出目录", Toast.LENGTH_SHORT).show()
            return null
        }
        return uri
    }

    fun exportFile(item: DownloadItem, options: ExportOptions) {
        val uri = exportUri() ?: return
        scope.launch {
            val result = container.downloadManager.exportFile(item, uri, options)
            result.onSuccess { Toast.makeText(context, "已导出", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    fun exportWork(workId: Long, options: ExportOptions) {
        val uri = exportUri() ?: return
        scope.launch {
            val result = container.downloadManager.exportWork(workId, uri, options)
            result.onSuccess { n -> Toast.makeText(context, "已导出 $n 个文件", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    fun preview(item: DownloadItem) {
        val path = item.localPath ?: return
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
            return
        }
        val title = item.title
        when {
            isImageFile(title) -> previewImage = path
            isSubtitleFile(title) -> scope.launch {
                previewText = runCatching { file.readText() }.getOrElse { "（读取失败）" }
            }
            else -> Toast.makeText(context, "该类型暂不支持预览", Toast.LENGTH_SHORT).show()
        }
    }

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
                                IconButton(onClick = { exportWorkId = workId }) { Icon(Icons.Filled.UploadFile, "导出") }
                                IconButton(onClick = { deleteWorkId = workId }) { Icon(Icons.Filled.Delete, "删除作品") }
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
            TopBar(
                workTitle,
                onBack = { selectedWork = null },
                actions = {
                    val failed = items.filter { it.status == DownloadItem.STATUS_FAILED }
                    if (failed.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch { failed.forEach { container.downloadManager.retry(it) } }
                        }) {
                            Icon(Icons.Filled.Refresh, "重试失败")
                        }
                    }
                }
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { item ->
                    val isAudio = isAudioFile(item.title)
                    val canPreview = item.status == DownloadItem.STATUS_DONE && !isAudio
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = canPreview) { preview(item) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                DownloadItem.STATUS_PAUSED -> Text("已暂停", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                DownloadItem.STATUS_FAILED -> Text("下载失败", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                else -> Text("排队中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        when (item.status) {
                            DownloadItem.STATUS_DONE -> {
                                if (isAudio) {
                                    IconButton(onClick = { play(item) }) { Icon(Icons.Filled.PlayArrow, "播放") }
                                } else {
                                    IconButton(onClick = { preview(item) }) {
                                        Icon(
                                            if (isImageFile(item.title)) Icons.Filled.Image else Icons.Filled.Description,
                                            "预览"
                                        )
                                    }
                                }
                                IconButton(onClick = { exportItem = item }) { Icon(Icons.Filled.UploadFile, "导出") }
                            }
                            DownloadItem.STATUS_DOWNLOADING, DownloadItem.STATUS_QUEUED -> {
                                IconButton(onClick = { scope.launch { container.downloadManager.pause(item.id) } }) { Icon(Icons.Filled.Pause, "暂停") }
                            }
                            DownloadItem.STATUS_PAUSED -> {
                                IconButton(onClick = { scope.launch { container.downloadManager.resume(item.id) } }) { Icon(Icons.Filled.PlayArrow, "继续") }
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

    previewImage?.let { path ->
        Dialog(onDismissRequest = { previewImage = null }) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                AsyncImage(
                    model = File(path),
                    contentDescription = "图片预览",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    previewText?.let { content ->
        AlertDialog(
            onDismissRequest = { previewText = null },
            title = { Text("文本 / 字幕预览") },
            text = {
                Text(
                    content,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                )
            },
            confirmButton = { TextButton(onClick = { previewText = null }) { Text("关闭") } }
        )
    }

    exportWorkId?.let { workId ->
        ExportDialog(
            onDismiss = { exportWorkId = null },
            onConfirm = { opts ->
                exportWorkId = null
                exportWork(workId, opts)
            }
        )
    }

    exportItem?.let { item ->
        ExportDialog(
            onDismiss = { exportItem = null },
            onConfirm = { opts ->
                exportItem = null
                exportFile(item, opts)
            }
        )
    }

    deleteWorkId?.let { workId ->
        val title = downloads.firstOrNull { it.workId == workId }?.workTitle ?: ""
        AlertDialog(
            onDismissRequest = { deleteWorkId = null },
            title = { Text("删除作品") },
            text = { Text("确定要删除「$title」下所有已下载的文件吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteWorkId = null
                    scope.launch { container.downloadManager.deleteWork(workId) }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteWorkId = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun ExportDialog(onDismiss: () -> Unit, onConfirm: (ExportOptions) -> Unit) {
    var convertSubtitles by remember { mutableStateOf(true) }
    var includeCover by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出选项") },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { convertSubtitles = !convertSubtitles }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = convertSubtitles, onCheckedChange = { convertSubtitles = it })
                    Text("转换字幕（VTT→LRC 并内嵌歌词）", style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    Modifier.fillMaxWidth().clickable { includeCover = !includeCover }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = includeCover, onCheckedChange = { includeCover = it })
                    Text("放入作品封面（folder.jpg）", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(ExportOptions(convertSubtitles, includeCover)) }) { Text("导出") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
