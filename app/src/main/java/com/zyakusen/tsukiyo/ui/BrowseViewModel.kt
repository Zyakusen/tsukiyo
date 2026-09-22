package com.zyakusen.tsukiyo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyakusen.tsukiyo.data.AppContainer
import com.zyakusen.tsukiyo.data.model.Work
import com.zyakusen.tsukiyo.util.PagingState
import kotlinx.coroutines.launch

/**
 * 首页浏览状态：跨导航保留已加载列表、排序与滚动位置。
 */
class BrowseViewModel(private val container: AppContainer) : ViewModel() {

    var sortKey by mutableStateOf(container.settingsStore.defaultSort)
        private set

    var sortAsc by mutableStateOf(false)
        private set

    var appliedSubtitlesOnly by mutableStateOf<Boolean?>(null)

    val paging = PagingState<Work> { page ->
        val resp = container.repository.getWorks(
            order = sortKey,
            sort = if (sortAsc) "asc" else "desc",
            page = page,
            pageSize = 20
        )
        (resp.works ?: emptyList()) to (resp.pagination?.totalCount ?: Int.MAX_VALUE)
    }

    init {
        viewModelScope.launch { paging.refresh() }
    }

    fun setSort(key: String) {
        sortKey = key
    }

    fun toggleSortAsc() {
        sortAsc = !sortAsc
    }

    fun refresh() {
        viewModelScope.launch { paging.refresh() }
    }
}
