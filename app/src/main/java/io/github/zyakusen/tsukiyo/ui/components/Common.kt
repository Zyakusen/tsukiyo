package io.github.zyakusen.tsukiyo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.zyakusen.tsukiyo.data.model.Work
import io.github.zyakusen.tsukiyo.player.PlayerManager
import io.github.zyakusen.tsukiyo.util.PagingState
import io.github.zyakusen.tsukiyo.util.formatCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** 多声优的分隔符（卡片上放不下时会从尾部省略，与社团同一套省略方式）。 */
const val VA_SEPARATOR = " / "

/**
 * 卡片信息配色（四个字段各一色，深浅主题都自适应）：
 *  * **社团** = 主题 primary（玫红/粉）——"出品方"最该被看到；
 *  * **声优** = 主题 secondary（紫/薰衣草）——与社团同权重但不同色系，区分"团体 vs 个人"；
 *  * **评分** = 琥珀金 —— 与"星级/评分"的直觉一致，暖色最能突出数字；按主题亮度自适应；
 *  * **下载数** = onSurfaceVariant（灰）—— 次要信息，不与前三者抢视线。
 */
@Composable
fun ratingColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFFC46B) else Color(0xFFB26A00)

@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    blur: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    // 封面来自 CDN 原图（0.44–2.7MB/张，无缩略图接口），一页 40 张要排队，偶发超时/限流很常见；
    // 而**失败的请求不会被 Coil 缓存**，所以"切回这一页还是刚才那几张没出来、得等一会儿"。
    // 这里对失败自动重试最多 2 次（间隔 1.2s）：必须换一个请求（加参数）Coil 才会真的重发，
    // 直接重组是没用的（同样的 model 会命中"已失败"状态）。
    var retryKey by remember(url) { mutableStateOf(0) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url, failed, retryKey) {
        if (failed && retryKey < 2) {
            delay(1200)
            failed = false
            retryKey++
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (url != null) {
            AsyncImage(
                model = if (retryKey == 0) url else ImageRequest.Builder(context)
                    .data(url)
                    .setParameter("coverRetry", retryKey)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blur) Modifier.blur(20.dp) else Modifier),
                contentScale = contentScale,
                onError = { failed = true }
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("无封面", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (blur) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "NSFW",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun RatingBar(rating: Double, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            val icon = when {
                rating >= i + 1 -> Icons.Filled.Star
                rating >= i + 0.5 -> Icons.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFFFC107),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 作品卡片。信息排布（三行）：
 * ```
 * 封面
 * 标题（最多两行）
 * 社团 ………………… 4.5(86)      ← 评分（一位小数 + 评论人数）靠右；社团占满剩余，长了就省略
 * 声优 / 声优 ……… DL 37.4万    ← 下载数靠右；声优靠左，长了就省略
 * ```
 * 右侧两块都只占自身宽度，所以它们短的时候，空出来的位置会让给左边的社团/声优。
 *
 * @param showRating 收藏页/历史页不显示平均评分
 * @param showDownload 收藏页/历史页不显示下载数
 */
@Composable
fun WorkCard(
    work: Work,
    blurNsfw: Boolean = false,
    badge: String? = null,
    showRating: Boolean = true,
    showDownload: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box {
            CoverImage(
                url = work.coverUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentDescription = work.title,
                blur = blurNsfw && work.nsfw == true
            )
            if (badge != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = work.title ?: "",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 2
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = work.circleName ?: "",
                style = MaterialTheme.typography.labelSmall,
                // 社团：主题强调色
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // 评论数为 0 也显示 0.0(0)：与详情页一致，避免"这一格空了"让人猜是不是没加载出来
            val ratingText = if (showRating) {
                String.format(Locale.ROOT, "%.1f(%d)", work.rateAverage2dp ?: 0.0, work.rateCount ?: 0)
            } else null
            if (ratingText != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = ratingText,
                    style = MaterialTheme.typography.labelSmall,
                    // 评分：琥珀金
                    color = ratingColor(),
                    maxLines = 1
                )
            }
        }
        val vaText = work.vas?.mapNotNull { it.name }?.filter { it.isNotBlank() }?.joinToString(VA_SEPARATOR)
        if (vaText != null || showDownload) {
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vaText != null) {
                    Text(
                        text = vaText,
                        style = MaterialTheme.typography.labelSmall,
                        // 声优：主题次色
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (showDownload) {
                    // 下载数为 0 也要显示；formatCount 在 ≥10000 时用「万」，最长 4 个字符，宽度有界
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "DL ${formatCount(work.dlCount)}",
                        style = MaterialTheme.typography.labelSmall,
                        // 下载数：次要灰
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    dimmed: Boolean = false
) {
    val shape = RoundedCornerShape(14.dp)
    val clickModifier = if (onClick != null || onLongClick != null) {
        Modifier.pointerInput(onClick, onLongClick) {
            detectTapGestures(
                onTap = { onClick?.invoke() },
                onLongPress = { onLongClick?.invoke() }
            )
        }
    } else {
        Modifier
    }
    Surface(
        shape = shape,
        color = if (dimmed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.clip(shape).then(clickModifier)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EmptyState(text: String = "暂无内容", modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorState(message: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message ?: "加载失败", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Surface(onClick = onRetry, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) {
            Text("重试", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

/** 通用分页网格 */
@Composable
fun <T> PagedGrid(
    state: PagingState<T>,
    columns: Int = 2,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    key: ((T) -> Any)? = null,
    itemContent: @Composable (T) -> Unit
) {
    val gridState: LazyGridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.items.isNotEmpty()) {
            scope.launch { state.loadMore() }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(state.items, key = key?.let { k -> { _, item -> k(item) } }) { _, item ->
            itemContent(item)
        }
        if (state.loading) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                LoadingIndicator()
            }
        }
        if (state.error != null && state.items.isEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                ErrorState(state.error) { scope.launch { state.refresh() } }
            }
        }
    }
}

/** 底部迷你播放条 */
@Composable
fun MiniPlayerBar(onExpand: () -> Unit) {
    val state by PlayerManager.uiState.collectAsState()
    val track = state.currentTrack ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverImage(track.coverUrl, Modifier.size(42.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.workTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { PlayerManager.togglePlay(context) }) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "播放/暂停"
                )
            }
            IconButton(onClick = { PlayerManager.next(context) }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一首")
            }
        }
    }
}
