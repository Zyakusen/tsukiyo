package io.github.zyakusen.tsukiyo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.zyakusen.tsukiyo.data.AppContainer
import io.github.zyakusen.tsukiyo.data.model.Work
import io.github.zyakusen.tsukiyo.util.FilterType
import io.github.zyakusen.tsukiyo.util.PagingState
import io.github.zyakusen.tsukiyo.util.SearchFilter
import io.github.zyakusen.tsukiyo.util.SearchPreset
import kotlinx.coroutines.launch

/**
 * 搜索页状态：跨导航保留关键词、筛选、排序与结果列表。
 */
class SearchViewModel(private val container: AppContainer) : ViewModel() {

    var keyword by mutableStateOf("")
        private set

    var submitted by mutableStateOf("")
        private set

    var sortKey by mutableStateOf("release")
        private set

    var sortAsc by mutableStateOf(false)
        private set

    val filters = mutableStateListOf<SearchFilter>()

    val query: String
        get() = buildString {
            if (submitted.isNotBlank()) append(submitted.trim())
            filters.forEach { append(' ').append(it.raw) }
        }.trim()

    val paging = PagingState<Work> { page ->
        val q = query
        if (q.isBlank()) {
            (emptyList<Work>()) to 0
        } else {
            val resp = container.repository.search(
                query = q,
                order = sortKey,
                sort = if (sortAsc) "asc" else "desc",
                page = page,
                pageSize = 20
            )
            (resp.works ?: emptyList()) to (resp.pagination?.totalCount ?: Int.MAX_VALUE)
        }
    }

    fun applyPendingPreset() {
        val f = SearchPreset.pendingFilter ?: return
        SearchPreset.pendingFilter = null
        if (filters.none { it.raw == f.raw }) {
            filters.add(f)
            refresh()
        }
    }

    fun updateKeyword(value: String) {
        keyword = value
    }

    fun clearSearch() {
        keyword = ""
        submitted = ""
        refresh()
    }

    fun commitSearch() {
        submitted = keyword
        refresh()
    }

    fun setSort(key: String) {
        sortKey = key
        refresh()
    }

    fun toggleSortAsc() {
        sortAsc = !sortAsc
        refresh()
    }

    fun addFilter(filter: SearchFilter) {
        filters.add(filter)
        refresh()
    }

    fun toggleFilter(filter: SearchFilter) {
        val idx = filters.indexOfFirst { it.raw == filter.raw }
        if (idx >= 0) {
            filters[idx] = filter.toggleExclude()
            refresh()
        }
    }

    fun removeFilter(filter: SearchFilter) {
        filters.removeAll { it.raw == filter.raw }
        refresh()
    }

    fun refresh() {
        if (query.isNotBlank()) {
            viewModelScope.launch { paging.refresh() }
        } else {
            paging.items.clear()
        }
    }
}
