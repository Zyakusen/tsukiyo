package io.github.zyakusen.tsukiyo.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import io.github.zyakusen.tsukiyo.data.entity.DownloadItem
import io.github.zyakusen.tsukiyo.data.model.Playlist
import io.github.zyakusen.tsukiyo.data.model.Track
import io.github.zyakusen.tsukiyo.data.model.Work
import io.github.zyakusen.tsukiyo.player.PlayerManager
import io.github.zyakusen.tsukiyo.ui.LocalContainer
import io.github.zyakusen.tsukiyo.ui.components.CoverImage
import io.github.zyakusen.tsukiyo.ui.components.RatingBar
import io.github.zyakusen.tsukiyo.ui.components.TagChip
import io.github.zyakusen.tsukiyo.ui.components.WorkCard
import io.github.zyakusen.tsukiyo.ui.navigation.Routes
import io.github.zyakusen.tsukiyo.ui.navigation.navigateToSearch
import io.github.zyakusen.tsukiyo.util.FilterType
import io.github.zyakusen.tsukiyo.util.ProgressOption
import io.github.zyakusen.tsukiyo.util.SearchFilter
import io.github.zyakusen.tsukiyo.util.SearchPreset
import io.github.zyakusen.tsukiyo.util.baseName
import io.github.zyakusen.tsukiyo.util.displayPlaylistName
import io.github.zyakusen.tsukiyo.util.extension
import io.github.zyakusen.tsukiyo.util.findSmartPath
import io.github.zyakusen.tsukiyo.util.formatDuration
import io.github.zyakusen.tsukiyo.util.formatSize
import io.github.zyakusen.tsukiyo.util.isLowVoteTag
import io.github.zyakusen.tsukiyo.util.progressOptions
import io.github.zyakusen.tsukiyo.util.saveImageToGallery
import io.github.zyakusen.tsukiyo.util.saveImageFileToGallery
import io.github.zyakusen.tsukiyo.util.tagDisplayName
import io.github.zyakusen.tsukiyo.util.toPlayable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class FileKind { AUDIO, TEXT, IMAGE, PDF, OTHER }

private data class FilterAction(val type: FilterType, val name: String, val label: String)

private fun fileKind(title: String): FileKind {
    val t = title.lowercase()
    return when {
        t.endsWith(".lrc") || t.endsWith(".vtt") || t.endsWith(".srt") || t.endsWith(".ass") || t.endsWith(".ssa") || t.endsWith(".txt") -> FileKind.TEXT
        t.endsWith(".jpg") || t.endsWith(".jpeg") || t.endsWith(".png") || t.endsWith(".webp") || t.endsWith(".gif") || t.endsWith(".bmp") -> FileKind.IMAGE
        t.endsWith(".pdf") -> FileKind.PDF
        t.endsWith(".wav") || t.endsWith(".mp3") || t.endsWith(".m4a") || t.endsWith(".aac") || t.endsWith(".flac") || t.endsWith(".ogg") || t.endsWith(".opus") || t.endsWith(".mp4") || t.endsWith(".webm") -> FileKind.AUDIO
        else -> FileKind.OTHER
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkDetailScreen(workId: Long, navController: NavHostController) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth by container.authManager.state.collectAsState()
    val settings by container.settingsStore.state.collectAsState()

    var work by remember { mutableStateOf<Work?>(null) }
    var rawTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var subtitleMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var currentPath by remember { mutableStateOf<List<Track>>(emptyList()) }

    val smartPathFingerprint = "${settings.smartPathEnabled}|${settings.sePreference}|${settings.audioTypeOrder.joinToString(",")}"
    var savedPathKey by rememberSaveable(smartPathFingerprint) { mutableStateOf<String?>(null) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    val audioTracks = remember(rawTracks) {
        container.repository.flattenAudioTracks(rawTracks)
    }

    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var similarWorks by remember { mutableStateOf<List<Work>>(emptyList()) }

    var showRating by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var previewText by remember { mutableStateOf<String?>(null) }
    var previewImage by remember { mutableStateOf<Any?>(null) }
    var filterAction by remember { mutableStateOf<FilterAction?>(null) }

    val downloads by container.downloadManager.observeByWork(workId).collectAsState(initial = emptyList())

    LaunchedEffect(workId, smartPathFingerprint, reloadKey) {
        loading = true
        error = null
        try {
            val (w, raw) = container.repository.getWorkWithTracks(workId, force = reloadKey > 0)
            val local = container.reviewStore.get(workId)
            work = w.copy(
                userRating = w.userRating ?: local?.rating,
                reviewText = w.reviewText ?: local?.reviewText,
                progress = w.progress ?: local?.progress
            )
            container.historyStore.record(w)
            rawTracks = raw
            subtitleMap = collectSubtitleMap(raw)

            if (savedPathKey == null) {
                currentPath = if (settings.smartPathEnabled) {
                    findSmartPath(raw, settings.sePreference, settings.audioTypeOrder)
                } else {
                    emptyList()
                }
                savedPathKey = serializePath(currentPath)
            } else {
                currentPath = resolvePath(raw, savedPathKey ?: "")
            }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(workId) {
        if (auth.isRealUser) {
            runCatching {
                val exist = container.repository.getWorkExistStatus(workId)
                playlists = exist.playlists ?: emptyList()
            }
        }
        runCatching { similarWorks = container.repository.similarWorks(workId).works ?: emptyList() }
    }

    fun updatePath(newPath: List<Track>) {
        currentPath = newPath
        savedPathKey = serializePath(newPath)
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun localFileFor(track: Track): File? {
        val hash = track.hash ?: return null
        return downloads.firstOrNull { it.id == hash && it.status == DownloadItem.STATUS_DONE && it.localPath != null }
            ?.localPath?.let { File(it) }?.takeIf { it.exists() }
    }

    fun buildQueue(): List<io.github.zyakusen.tsukiyo.player.PlayableTrack> {
        val w = work ?: return emptyList()
        return audioTracks.map { track ->
            val onlineSubtitle = subtitleMap[track.hash]
            val hash = track.hash
            val dl = hash?.let { h ->
                downloads.firstOrNull { it.id == h && it.status == DownloadItem.STATUS_DONE && it.localPath != null }
            }
            if (dl != null && File(dl.localPath!!).exists()) {
                val localSub = localSubtitleFor(track.title ?: "", dl.folderPath, downloads)
                track.toPlayable(w, localSub ?: onlineSubtitle).copy(
                    highUrl = Uri.fromFile(File(dl.localPath!!)).toString(),
                    lowUrl = null
                )
            } else {
                track.toPlayable(w, onlineSubtitle)
            }
        }
    }

    fun playFromIndex(index: Int) {
        val list = buildQueue()
        if (list.isEmpty()) return
        PlayerManager.playQueue(context, list, index.coerceIn(0, list.lastIndex), settings.audioQuality)
    }

    val currentChildren: List<Track> = if (currentPath.isEmpty()) rawTracks else (currentPath.last().children ?: emptyList())

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                work?.title ?: "作品详情",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { reloadKey++ }) { Text("重试") }
            }
            work != null -> {
                val w = work!!
                LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                CoverImage(w.coverUrl, Modifier.width(120.dp).aspectRatio(1f))
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(w.title ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    if (w.circleName != null) {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            TagChip(
                                                text = "社团：${w.circleName}",
                                                onClick = {
                                                    SearchPreset.pending = SearchPreset.Pending(SearchFilter(FilterType.CIRCLE, w.circleName, w.circleName, false), true)
                                                    navController.navigateToSearch()
                                                },
                                                onLongClick = { filterAction = FilterAction(FilterType.CIRCLE, w.circleName, w.circleName) }
                                            )
                                        }
                                    }
                                    if (w.vas?.isNotEmpty() == true) {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)) {
                                            w.vas!!.forEach { va ->
                                                val name = va.name ?: return@forEach
                                                TagChip(
                                                    text = name,
                                                    onClick = {
                                                        SearchPreset.pending = SearchPreset.Pending(SearchFilter(FilterType.VA, name, name, false), true)
                                                        navController.navigateToSearch()
                                                    },
                                                    onLongClick = { filterAction = FilterAction(FilterType.VA, name, name) }
                                                )
                                            }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RatingBar(w.rateAverage2dp ?: 0.0)
                                        Text(" ${w.rateAverage2dp ?: "-"} (${w.rateCount ?: 0})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            val meta = buildList {
                                w.sourceId?.let { add(it) }
                                w.release?.let { add(it) }
                                w.ageCategoryString?.let { add(it) }
                                w.duration?.let { add("时长 ${formatDuration(it)}") }
                                add("DL ${w.dlCount ?: 0}")
                            }
                            Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            if (w.tags?.isNotEmpty() == true) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    w.tags!!.forEach { tag ->
                                        val displayName = tagDisplayName(tag, settings.tagLanguage)
                                        TagChip(
                                            text = displayName,
                                            onClick = {
                                                SearchPreset.pending = SearchPreset.Pending(SearchFilter(FilterType.TAG, displayName, displayName, false), true)
                                                navController.navigateToSearch()
                                            },
                                            onLongClick = { filterAction = FilterAction(FilterType.TAG, displayName, displayName) },
                                            dimmed = isLowVoteTag(tag)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { if (auth.isRealUser) showAddToPlaylist = true else toast("请先登录") }) {
                                    Icon(Icons.Filled.PlaylistAdd, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("加入播放列表")
                                }
                                OutlinedButton(onClick = { if (auth.isRealUser) showProgress = true else toast("请先登录") }) {
                                    Icon(Icons.Filled.TaskAlt, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("标记进度")
                                }
                                OutlinedButton(onClick = { if (auth.isRealUser) showRating = true else toast("请登录后评分") }) {
                                    Icon(Icons.Filled.Star, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("评分")
                                }
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        val count = container.downloadManager.enqueueWork(w, rawTracks)
                                        toast("已加入下载队列（$count 个文件）")
                                    }
                                }) {
                                    Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("下载")
                                }
                            }
                            val editions = w.otherLanguageEditionsInDb.orEmpty()
                                .filter { it.id != null }
                                .mapNotNull { e -> e.id?.let { id -> (e.lang ?: e.sourceId ?: "") to id } }
                            if (editions.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("语言版本：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    editions.forEach { (label, targetId) ->
                                        TagChip(
                                            text = label,
                                            onClick = { navController.navigate(Routes.work(targetId)) }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("文件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            val breadcrumb = (listOf("根目录") + currentPath.map { it.title ?: "" }).joinToString(" / ")
                            Text(breadcrumb, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    if (currentPath.isNotEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().clickable { updatePath(currentPath.dropLast(1)) }.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("返回上一级", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    items(currentChildren, key = { it.hash ?: it.title ?: "" }) { track ->
                        FileRow(
                            track = track,
                            downloaded = downloads.any { it.id == (track.hash ?: "") && it.status == io.github.zyakusen.tsukiyo.data.entity.DownloadItem.STATUS_DONE },
                            onEnterFolder = { updatePath(currentPath + track) },
                            onPlayAudio = {
                                val globalIndex = audioTracks.indexOf(track)
                                playFromIndex(if (globalIndex >= 0) globalIndex else 0)
                            },
                            onPreview = {
                                val local = localFileFor(track)
                                when (fileKind(track.title ?: "")) {
                                    FileKind.IMAGE -> previewImage = local ?: track.mediaStreamUrl
                                    FileKind.TEXT -> scope.launch {
                                        previewText = local?.let { runCatching { it.readText() }.getOrElse { "（读取失败）" } }
                                            ?: fetchText(track.mediaStreamUrl)
                                    }
                                    FileKind.PDF -> {
                                        if (local != null) openPdfFile(context, local)
                                        else openPdf(context, track.mediaDownloadUrl ?: track.mediaStreamUrl)
                                    }
                                    else -> toast("该类型暂不支持预览")
                                }
                            },
                            onDownload = {
                                scope.launch {
                                    container.downloadManager.enqueue(track, w, currentPath.joinToString("/") { it.title ?: "" })
                                    toast("已加入下载队列")
                                }
                            }
                        )
                    }

                    if (similarWorks.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(top = 12.dp)) {
                                Text(
                                    "相似作品",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(similarWorks, key = { it.id ?: 0L }) { sw ->
                                        Box(Modifier.width(128.dp)) {
                                            WorkCard(sw, blurNsfw = !settings.showNsfw) {
                                                sw.id?.let { navController.navigate(Routes.work(it)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRating && work != null) {
        RatingDialog(
            current = work!!.userRating,
            currentText = work!!.reviewText,
            onDismiss = { showRating = false },
            onSubmit = { rating, text ->
                scope.launch {
                    runCatching {
                        container.repository.rateWork(workId, rating, text)
                        container.reviewStore.saveRating(workId, rating, text)
                    }
                        .onSuccess {
                            work = work?.copy(userRating = rating, reviewText = text)
                            toast("评分成功")
                            showRating = false
                        }
                        .onFailure { toast("评分失败：${it.message}") }
                }
            }
        )
    }

    if (showProgress) {
        ProgressDialog(
            current = work?.progress,
            onDismiss = { showProgress = false },
            onPick = { p ->
                scope.launch {
                    runCatching {
                        container.repository.markProgress(workId, p.value)
                        container.reviewStore.saveProgress(workId, p.value)
                    }
                        .onSuccess {
                            work = work?.copy(progress = p.value)
                            toast("已标记：${p.label}")
                            showProgress = false
                        }
                        .onFailure { toast("操作失败：${it.message}") }
                }
            },
            onUnmark = {
                scope.launch {
                    runCatching {
                        container.repository.unmark(workId)
                        container.reviewStore.saveProgress(workId, null)
                    }
                        .onSuccess {
                            work = work?.copy(progress = null)
                            toast("已取消标记")
                            showProgress = false
                        }
                        .onFailure { toast("操作失败：${it.message}") }
                }
            }
        )
    }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showAddToPlaylist = false },
            onToggle = { pl, shouldAdd ->
                scope.launch {
                    runCatching {
                        if (shouldAdd) container.repository.addWorksToPlaylist(pl.id!!, listOf(workId))
                        else container.repository.removeWorksFromPlaylist(pl.id!!, listOf(workId))
                    }.onSuccess {
                        playlists = playlists.map { if (it.id == pl.id) it.copy(exist = shouldAdd) else it }
                        toast(if (shouldAdd) "已添加到 ${displayPlaylistName(pl.name)}" else "已从 ${displayPlaylistName(pl.name)} 移除")
                    }.onFailure { toast("操作失败：${it.message}") }
                }
            }
        )
    }

    previewText?.let { TextPreviewDialog(content = it, onDismiss = { previewText = null }) }
    previewImage?.let { ImagePreviewDialog(model = it, onDismiss = { previewImage = null }) }

    filterAction?.let { action ->
        FilterActionDialog(
            title = action.label,
            onDismiss = { filterAction = null },
            onInclude = {
                SearchPreset.pending = SearchPreset.Pending(SearchFilter(action.type, action.name, action.label, false), false)
                filterAction = null
                navController.navigateToSearch()
            },
            onExclude = {
                SearchPreset.pending = SearchPreset.Pending(SearchFilter(action.type, action.name, action.label, true), false)
                filterAction = null
                navController.navigateToSearch()
            }
        )
    }
}

// ---------- 文件 ----------

private const val PATH_SEP = "\u0001"

private fun serializePath(path: List<Track>): String =
    path.joinToString(PATH_SEP) { it.title ?: "" }

private fun resolvePath(nodes: List<Track>, key: String): List<Track> {
    if (key.isEmpty()) return emptyList()
    val titles = key.split(PATH_SEP)
    var current = nodes
    val result = mutableListOf<Track>()
    for (t in titles) {
        val folder = current.firstOrNull { it.type == "folder" && it.title == t }
        if (folder == null) break
        result.add(folder)
        current = folder.children ?: emptyList()
    }
    return result
}

private fun collectSubtitleMap(nodes: List<Track>): Map<String, String> {
    val map = mutableMapOf<String, String>()

    // 全局索引：字幕标题 -> 首个 URL，用于跨目录回退。
    val textByTitle = mutableMapOf<String, String>()
    fun indexTexts(list: List<Track>) {
        for (t in list) {
            if (t.type == "text" && !t.mediaStreamUrl.isNullOrBlank()) {
                val title = t.title ?: ""
                if (title.isNotBlank() && !textByTitle.containsKey(title)) textByTitle[title] = t.mediaStreamUrl!!
            }
            if (t.type == "folder") t.children?.let { indexTexts(it) }
        }
    }
    indexTexts(nodes)

    fun walk(list: List<Track>) {
        val siblingByTitle = mutableMapOf<String, String>()
        for (t in list) {
            if (t.type == "text" && !t.mediaStreamUrl.isNullOrBlank()) {
                val title = t.title ?: ""
                if (title.isNotBlank() && !siblingByTitle.containsKey(title)) siblingByTitle[title] = t.mediaStreamUrl!!
            }
        }
        for (a in list) {
            if (a.type != "audio") continue
            val hash = a.hash ?: continue
            val audioTitle = a.title ?: continue
            var url: String? = null
            // 1) 同级目录同名字幕
            for (name in subtitleNamesFor(audioTitle)) {
                url = siblingByTitle[name]
                if (url != null) break
            }
            // 2) 跨目录同名回退
            if (url == null) {
                for (name in subtitleNamesFor(audioTitle)) {
                    url = textByTitle[name]
                    if (url != null) break
                }
            }
            if (url != null) map[hash] = url
        }
        for (t in list) {
            if (t.type == "folder") t.children?.let { walk(it) }
        }
    }
    walk(nodes)
    return map
}

/** 音频的字幕候选文件名（vtt/srt 为文件名追加、lrc/srt 为扩展名替换）。 */
private fun subtitleNamesFor(audioTitle: String): List<String> {
    val base = baseName(audioTitle)
    return listOf(audioTitle + ".vtt", audioTitle + ".srt", "$base.lrc", "$base.srt")
}

/** 在已下载文件中查找与音频匹配的本地字幕文件（同级目录优先，再跨目录回退）。 */
private fun localSubtitleFor(audioTitle: String, folderPath: String, downloads: List<DownloadItem>): String? {
    val names = subtitleNamesFor(audioTitle)
    val done = downloads.filter {
        it.status == DownloadItem.STATUS_DONE && it.localPath != null && it.title in names
    }
    fun resolve(items: List<DownloadItem>): String? =
        items.sortedBy { names.indexOf(it.title) }
            .mapNotNull { it.localPath?.let(::File) }
            .firstOrNull { it.exists() }
            ?.let { Uri.fromFile(it).toString() }
    resolve(done.filter { it.folderPath == folderPath })?.let { return it }
    return resolve(done)
}

private suspend fun fetchText(url: String?): String = withContext(Dispatchers.IO) {
    if (url.isNullOrBlank()) return@withContext "（无内容）"
    runCatching {
        val req = okhttp3.Request.Builder().url(url).build()
        io.github.zyakusen.tsukiyo.data.api.NetworkModule.downloadClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() ?: "（空内容）" else "（加载失败 ${resp.code}）"
        }
    }.getOrElse { "（加载失败：${it.message}）" }
}

private fun openPdf(context: android.content.Context, url: String?) {
    if (url.isNullOrBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun openPdfFile(context: android.content.Context, file: File) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

@Composable
private fun FileRow(
    track: Track,
    downloaded: Boolean,
    onEnterFolder: () -> Unit,
    onPlayAudio: () -> Unit,
    onPreview: () -> Unit,
    onDownload: () -> Unit
) {
    val isFolder = track.type == "folder"
    val kind = fileKind(track.title ?: "")
    val icon = when {
        isFolder -> Icons.Filled.Folder
        kind == FileKind.AUDIO -> Icons.Filled.PlayArrow
        kind == FileKind.TEXT -> Icons.Filled.Description
        kind == FileKind.IMAGE -> Icons.Filled.Image
        kind == FileKind.PDF -> Icons.Filled.PictureAsPdf
        else -> Icons.Filled.InsertDriveFile
    }
    val iconTint = when {
        isFolder -> MaterialTheme.colorScheme.secondary
        kind == FileKind.AUDIO -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    when {
                        isFolder -> onEnterFolder()
                        kind == FileKind.AUDIO -> onPlayAudio()
                        else -> onPreview()
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title ?: "", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                if (!isFolder) {
                    Row {
                        track.extension().takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        track.duration?.let {
                            Text(" · ${formatDuration(it.toLong())}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        track.size?.let {
                            Text(" · ${formatSize(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (isFolder) {
                Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (kind == FileKind.AUDIO) {
                IconButton(onClick = onPlayAudio) { Icon(Icons.Filled.PlayArrow, "播放") }
                IconButton(onClick = onDownload) {
                    Icon(if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download, "下载", tint = if (downloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                IconButton(onClick = onDownload) {
                    Icon(if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download, "下载", tint = if (downloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------- 对话框 ----------

@Composable
private fun RatingDialog(current: Double?, currentText: String?, onDismiss: () -> Unit, onSubmit: (Double?, String?) -> Unit) {
    var rating by remember { mutableStateOf(current ?: 0.0) }
    var text by remember { mutableStateOf(currentText ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("评分") },
        text = {
            Column {
                Row {
                    repeat(5) { i ->
                        IconButton(onClick = { rating = (i + 1).toDouble() }) {
                            Icon(if (rating >= i + 1) Icons.Filled.Star else Icons.Filled.StarBorder, "star", tint = if (rating >= i + 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("评论（可选）") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(rating, text.ifBlank { null }) }) { Text("提交") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ProgressDialog(current: String?, onDismiss: () -> Unit, onPick: (ProgressOption) -> Unit, onUnmark: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标记进度") },
        text = {
            Column {
                progressOptions.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == p.value, onClick = { onPick(p) })
                        Spacer(Modifier.width(4.dp))
                        Text(p.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onUnmark, modifier = Modifier.fillMaxWidth()) { Text("取消标记") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun AddToPlaylistDialog(playlists: List<Playlist>, onDismiss: () -> Unit, onToggle: (Playlist, Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入播放列表") },
        text = {
            if (playlists.isEmpty()) Text("暂无播放列表")
            else LazyColumn {
                playlists.forEach { pl ->
                    item {
                        val checked = pl.exist == true
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(pl, !checked) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { onToggle(pl, it) })
                            Spacer(Modifier.width(8.dp))
                            Text(displayPlaylistName(pl.name), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun FilterActionDialog(title: String, onDismiss: () -> Unit, onInclude: () -> Unit, onExclude: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("筛选：$title") },
        text = { Text("选择正选（仅含此项）还是反选（排除此项）") },
        confirmButton = { TextButton(onClick = onInclude) { Text("正选") } },
        dismissButton = {
            Row {
                TextButton(onClick = onExclude) { Text("反选") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun TextPreviewDialog(content: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("字幕 / 文本预览") },
        text = {
            Column(Modifier.fillMaxWidth().height(400.dp)) {
                Text(content, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun ImagePreviewDialog(model: Any?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxWidth().padding(8.dp)) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(model) {
                            detectTapGestures(
                                onLongPress = {
                                    scope.launch {
                                        val name = "asmr_${System.currentTimeMillis()}.jpg"
                                        val ok = when (model) {
                                            is File -> saveImageFileToGallery(context, model, name)
                                            is String -> saveImageToGallery(context, model, name)
                                            else -> false
                                        }
                                        Toast.makeText(context, if (ok) "已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                )
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.Close, "关闭", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    "长按图片可保存到相册",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp)
                )
            }
        }
    }
}
