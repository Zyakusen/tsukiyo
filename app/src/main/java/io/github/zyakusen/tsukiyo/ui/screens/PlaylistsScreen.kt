package io.github.zyakusen.tsukiyo.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.zyakusen.tsukiyo.data.model.Playlist
import io.github.zyakusen.tsukiyo.data.model.Work
import io.github.zyakusen.tsukiyo.ui.LocalContainer
import io.github.zyakusen.tsukiyo.ui.components.CoverImage
import io.github.zyakusen.tsukiyo.ui.components.EmptyState
import io.github.zyakusen.tsukiyo.ui.components.PagedGrid
import io.github.zyakusen.tsukiyo.ui.components.TopBar
import io.github.zyakusen.tsukiyo.ui.components.WorkCard
import io.github.zyakusen.tsukiyo.ui.navigation.Routes
import io.github.zyakusen.tsukiyo.util.PagingState
import io.github.zyakusen.tsukiyo.util.displayPlaylistName
import io.github.zyakusen.tsukiyo.util.isSystemPlaylist
import io.github.zyakusen.tsukiyo.util.privacyLabel
import io.github.zyakusen.tsukiyo.util.privacyOptions
import io.github.zyakusen.tsukiyo.util.progressLabel
import io.github.zyakusen.tsukiyo.util.progressOptions
import kotlinx.coroutines.launch

private fun favoriteBadge(work: Work): String? {
    val p = work.progress?.let { progressLabel(it) }
    val r = work.userRating?.let { "★$it" }
    return listOfNotNull(p, r).joinToString(" · ").ifBlank { null }
}

@Composable
fun FavoritesScreen(navController: NavHostController) {
    val container = LocalContainer.current
    val auth by container.authManager.state.collectAsState()
    val settings by container.settingsStore.state.collectAsState()

    var filter by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val paging = remember(filter, auth.isRealUser, settings.subtitlesOnly) {
        PagingState<Work> { page ->
            if (!auth.isRealUser) {
                (emptyList<Work>()) to 0
            } else {
                val resp = container.repository.getReviews(
                    order = "updated_at",
                    sort = "desc",
                    page = page,
                    pageSize = 20,
                    filter = filter
                )
                (resp.works ?: emptyList()) to (resp.pagination?.totalCount ?: Int.MAX_VALUE)
            }
        }
    }

    LaunchedEffect(paging) { if (auth.isRealUser) paging.refresh() }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            "我的收藏",
            actions = {
                IconButton(onClick = { scope.launch { paging.refresh() } }) {
                    Icon(Icons.Filled.Refresh, "刷新")
                }
            }
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SelectableChip("全部", filter == null) { filter = null } }
            items(progressOptions) { p ->
                SelectableChip(p.label, filter == p.value) { filter = p.value }
            }
        }

        when {
            !auth.isRealUser -> EmptyState("请先登录以查看")
            paging.items.isEmpty() && !paging.refreshing -> EmptyState("暂无收藏，去标记进度或评分吧")
            else -> PagedGrid(state = paging, key = { it.id ?: 0L }) { work ->
                WorkCard(work, blurNsfw = !settings.showNsfw, badge = favoriteBadge(work)) {
                    work.id?.let { navController.navigate(Routes.work(it)) }
                }
            }
        }
    }
}

@Composable
fun PlaylistsScreen(navController: NavHostController) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val auth by container.authManager.state.collectAsState()

    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }

    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            runCatching { container.repository.getPlaylists(pageSize = 100).playlists ?: emptyList() }
                .onSuccess { playlists = it }
        }
    }

    LaunchedEffect(auth.isRealUser) {
        if (auth.isRealUser) {
            loading = true
            runCatching { container.repository.getPlaylists(pageSize = 100).playlists ?: emptyList() }
                .onSuccess { playlists = it }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            "播放列表",
            onBack = { navController.popBackStack() },
            actions = {
                IconButton(onClick = { refresh() }) {
                    Icon(Icons.Filled.Refresh, "刷新")
                }
                TextButton(onClick = {
                    if (auth.isRealUser) creating = true else Toast.makeText(context, "请登录后创建", Toast.LENGTH_SHORT).show()
                }) { Text("新建") }
            }
        )
        when {
            !auth.isRealUser -> EmptyState("请先登录以查看")
            loading -> {}
            playlists.isEmpty() -> EmptyState("暂无播放列表")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(playlists, key = { it.id ?: "" }) { pl ->
                    val system = isSystemPlaylist(pl.name)
                    Row(
                        Modifier.fillMaxWidth().clickable { pl.id?.let { navController.navigate(Routes.playlist(it)) } }.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverImage(pl.mainCoverUrl, Modifier.size(56.dp), blur = false)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(displayPlaylistName(pl.name), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${pl.worksCount ?: 0} 个作品 · ${privacyLabel(pl.privacy)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editing = pl }) { Icon(Icons.Filled.Edit, "编辑") }
                        if (!system) {
                            IconButton(onClick = { deleting = pl }) { Icon(Icons.Filled.Delete, "删除") }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        PlaylistEditDialog(
            container = container,
            context = context,
            playlistId = null,
            initialName = "",
            initialDesc = "",
            initialPrivacy = 0,
            nameLocked = false,
            onDone = { creating = false; refresh() }
        )
    }
    editing?.let { pl ->
        PlaylistEditDialog(
            container = container,
            context = context,
            playlistId = pl.id,
            initialName = pl.name ?: "",
            initialDesc = pl.description ?: "",
            initialPrivacy = pl.privacy ?: 0,
            nameLocked = isSystemPlaylist(pl.name),
            onDone = { editing = null; refresh() }
        )
    }
    deleting?.let { pl ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除播放列表") },
            text = { Text("确定要删除「${displayPlaylistName(pl.name)}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        runCatching { container.repository.deletePlaylist(pl.id!!) }
                            .onSuccess { Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show(); refresh() }
                            .onFailure { Toast.makeText(context, "删除失败：${it.message}", Toast.LENGTH_SHORT).show() }
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun PlaylistEditDialog(
    container: io.github.zyakusen.tsukiyo.data.AppContainer,
    context: android.content.Context,
    playlistId: String?,
    initialName: String,
    initialDesc: String,
    initialPrivacy: Int,
    nameLocked: Boolean,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var desc by remember { mutableStateOf(initialDesc) }
    var privacy by remember { mutableStateOf(initialPrivacy) }
    var importText by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (playlistId == null) "新建播放列表" else "编辑播放列表") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true, enabled = !nameLocked, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(desc, { desc = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("可见性", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    privacyOptions.forEach { p ->
                        SelectableChip(p.label, privacy == p.value) { privacy = p.value }
                    }
                }
                if (playlistId != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("导入作品", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        importText, { importText = it },
                        label = { Text("输入包含 RJ 号的文字，如 RJ01650240") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    TextButton(
                        onClick = {
                            if (importText.isBlank()) return@TextButton
                            scope.launch {
                                importing = true
                                runCatching { container.repository.resolveWorkIdsByRjText(importText) }
                                    .onSuccess { ids ->
                                        if (ids.isEmpty()) {
                                            Toast.makeText(context, "未识别到作品", Toast.LENGTH_SHORT).show()
                                        } else {
                                            runCatching { container.repository.addWorksToPlaylist(playlistId!!, ids) }
                                                .onSuccess { Toast.makeText(context, "已导入 ${ids.size} 个作品", Toast.LENGTH_SHORT).show() }
                                                .onFailure { Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_SHORT).show() }
                                        }
                                    }
                                importing = false
                            }
                        },
                        enabled = !importing
                    ) { Text(if (importing) "导入中…" else "导入") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank() && !nameLocked) {
                        Toast.makeText(context, "请输入名称", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    scope.launch {
                        saving = true
                        val result = if (playlistId == null) {
                            runCatching { container.repository.createPlaylist(name, privacy, desc) }
                        } else {
                            runCatching { container.repository.editPlaylist(playlistId, name, privacy, desc) }
                        }
                        saving = false
                        result.onSuccess {
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                            onDone()
                        }.onFailure { Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_SHORT).show() }
                    }
                },
                enabled = !saving
            ) { Text(if (saving) "保存中…" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("取消") } }
    )
}

@Composable
fun PlaylistWorksScreen(playlistId: String, navController: NavHostController) {
    val container = LocalContainer.current
    var name by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(playlistId) {
        runCatching { name = displayPlaylistName(container.repository.getPlaylistMetadata(playlistId).name) }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(name ?: "播放列表", onBack = { navController.popBackStack() })
        PlaylistWorksGrid(playlistId, navController)
    }
}

@Composable
private fun PlaylistWorksGrid(playlistId: String, navController: NavHostController) {
    val container = LocalContainer.current
    val settings by container.settingsStore.state.collectAsState()
    val paging = remember(playlistId, settings.subtitlesOnly) {
        PagingState<Work> { page ->
            val resp = container.repository.getPlaylistWorks(playlistId, page, 20)
            (resp.works ?: emptyList()) to (resp.pagination?.totalCount ?: Int.MAX_VALUE)
        }
    }
    LaunchedEffect(paging) { paging.refresh() }

    if (paging.items.isEmpty() && !paging.refreshing) {
        EmptyState("暂无内容")
    }
    PagedGrid(state = paging, key = { it.id ?: 0L }) { work ->
        WorkCard(work, blurNsfw = !settings.showNsfw) { work.id?.let { navController.navigate(Routes.work(it)) } }
    }
}
