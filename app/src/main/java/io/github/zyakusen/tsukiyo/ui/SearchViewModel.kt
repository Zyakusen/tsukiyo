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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
        val p = SearchPreset.pending ?: return
        SearchPreset.pending = null
        if (p.clearExisting) {
            keyword = ""
            submitted = ""
            filters.clear()
        }
        val idx = filters.indexOfFirst { it.type == p.filter.type && it.name == p.filter.name }
        if (idx >= 0) filters[idx] = p.filter else filters.add(p.filter)
        refresh()
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
        refreshDebounced()
    }

    fun toggleSortAsc() {
        sortAsc = !sortAsc
        refreshDebounced()
    }

    fun addFilter(filter: SearchFilter) {
        val idx = filters.indexOfFirst { it.type == filter.type && it.name == filter.name }
        if (idx >= 0) filters[idx] = filter else filters.add(filter)
        refreshDebounced()
    }

    fun toggleFilter(filter: SearchFilter) {
        val idx = filters.indexOfFirst { it.raw == filter.raw }
        if (idx >= 0) {
            filters[idx] = filter.toggleExclude()
            refreshDebounced()
        }
    }

    fun removeFilter(filter: SearchFilter) {
        filters.removeAll { it.raw == filter.raw }
        refreshDebounced()
    }

    fun refresh() {
        debounceJob?.cancel()
        if (query.isNotBlank()) {
            viewModelScope.launch { paging.refresh() }
        } else {
            paging.items.clear()
        }
    }

    private var debounceJob: Job? = null

    private fun refreshDebounced() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(400)
            if (query.isNotBlank()) {
                paging.refresh()
            } else {
                paging.items.clear()
            }
        }
    }
}
