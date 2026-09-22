package com.zyakusen.tsukiyo.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 轻量分页状态：封装列表的加载与翻页逻辑。
 */
class PagingState<T>(
    private val fetch: suspend (page: Int) -> Pair<List<T>, Int>
) {
    val items = mutableStateListOf<T>()
    var page by mutableStateOf(0)
    var loading by mutableStateOf(false)
    var endReached by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var refreshing by mutableStateOf(false)

    suspend fun refresh() {
        if (refreshing) return
        refreshing = true
        error = null
        try {
            val (data, _) = fetch(1)
            items.clear()
            items.addAll(data)
            page = 1
            endReached = data.isEmpty()
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            refreshing = false
        }
    }

    suspend fun loadMore() {
        if (loading || endReached || refreshing) return
        loading = true
        try {
            val next = page + 1
            val (data, total) = fetch(next)
            items.addAll(data)
            page = next
            endReached = data.isEmpty() || items.size >= total
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }
}
