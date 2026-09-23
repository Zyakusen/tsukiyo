package io.github.zyakusen.tsukiyo.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.zyakusen.tsukiyo.player.PlayerManager
import io.github.zyakusen.tsukiyo.ui.LocalContainer
import io.github.zyakusen.tsukiyo.ui.components.CoverImage
import io.github.zyakusen.tsukiyo.ui.navigation.Routes
import io.github.zyakusen.tsukiyo.util.formatMs
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(navController: NavHostController) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val settings by container.settingsStore.state.collectAsState()
    val state by PlayerManager.uiState.collectAsState()
    val track = state.currentTrack

    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var showSleepDialog by remember { mutableStateOf(false) }
    val sleepRemainingMs by PlayerManager.sleepRemainingMs.collectAsState()

    if (track == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无播放内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Text(
                track.workTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).basicMarquee()
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.navigate(Routes.work(track.workId)) }) {
                Icon(Icons.AutoMirrored.Filled.LibraryBooks, "打开作品详情")
            }
            IconButton(onClick = { showSleepDialog = true }) {
                Icon(
                    Icons.Filled.Bedtime,
                    if (sleepRemainingMs != null) "定时：剩余 ${formatMs(sleepRemainingMs ?: 0L)}" else "定时停止",
                    tint = if (sleepRemainingMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { PlayerManager.setQuality(context, if (state.quality == "low") "high" else "low") }) {
                Text(
                    if (state.quality == "high") "高音质" else "流畅",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.quality == "high") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            CoverImage(track.coverUrl, Modifier.fillMaxWidth(0.8f).aspectRatio(1f))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (state.subtitleEnabled && !state.currentSubtitle.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        state.currentSubtitle ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                track.title,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )
            val position = dragPosition?.toLong() ?: state.positionMs
            Slider(
                value = position.toFloat().coerceIn(0f, state.durationMs.toFloat().coerceAtLeast(1f)),
                onValueChange = { dragPosition = it },
                valueRange = 0f..state.durationMs.toFloat().coerceAtLeast(1f),
                onValueChangeFinished = {
                    dragPosition?.let { PlayerManager.seekTo(context, it.toLong()) }
                    dragPosition = null
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatMs(position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(formatMs(state.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { PlayerManager.previous(context) }) { Icon(Icons.Filled.SkipPrevious, "上一首", Modifier.size(34.dp)) }
                IconButton(onClick = { PlayerManager.seekBy(context, -settings.rewindSeekSeconds * 1000L) }) { Icon(Icons.Filled.FastRewind, "后退 ${settings.rewindSeekSeconds} 秒", Modifier.size(30.dp)) }
                IconButton(onClick = { PlayerManager.togglePlay(context) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "暂停" else "播放", Modifier.size(56.dp))
                }
                IconButton(onClick = { PlayerManager.seekBy(context, settings.forwardSeekSeconds * 1000L) }) { Icon(Icons.Filled.FastForward, "前进 ${settings.forwardSeekSeconds} 秒", Modifier.size(30.dp)) }
                IconButton(onClick = { PlayerManager.next(context) }) { Icon(Icons.Filled.SkipNext, "下一首", Modifier.size(34.dp)) }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { PlayerManager.toggleSubtitle() }) {
                    Icon(
                        if (state.subtitleEnabled) Icons.Filled.Subtitles else Icons.Filled.SubtitlesOff,
                        "字幕",
                        tint = if (state.subtitleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.VolumeDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = state.volume,
                    onValueChange = { PlayerManager.setVolume(it) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Icon(Icons.Filled.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(
                "队列 ${state.currentIndex + 1}/${state.queueSize} · ${if (state.quality == "high") "高音质" else "流畅"}${if (state.subtitleLines.isNotEmpty()) " · 字幕" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            remainingMs = sleepRemainingMs,
            onDismiss = { showSleepDialog = false },
            onSetMinutes = { m ->
                PlayerManager.setSleepTimer(context, m)
                showSleepDialog = false
                Toast.makeText(context, "将在 $m 分钟后停止播放", Toast.LENGTH_SHORT).show()
            },
            onCancel = {
                PlayerManager.cancelSleepTimer()
                showSleepDialog = false
                Toast.makeText(context, "已取消定时", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerDialog(
    remainingMs: Long?,
    onDismiss: () -> Unit,
    onSetMinutes: (Int) -> Unit,
    onCancel: () -> Unit
) {
    var customText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定时停止") },
        text = {
            Column {
                if (remainingMs != null) {
                    Text("剩余 ${formatMs(remainingMs)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 20, 30, 45, 60).forEach { m ->
                        SelectableChip("$m 分钟", false) { onSetMinutes(m) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    customText,
                    { customText = it },
                    label = { Text("自定义分钟（上限 180）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        val m = customText.toIntOrNull()
                        if (m == null || m <= 0) { return@TextButton }
                        if (m > 180) { customText = "180" }
                        onSetMinutes(m.coerceIn(1, 180))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("自定义时长") }
                if (remainingMs != null) {
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("取消定时", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
