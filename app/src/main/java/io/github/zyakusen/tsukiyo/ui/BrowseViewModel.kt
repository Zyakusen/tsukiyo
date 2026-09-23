package io.github.zyakusen.tsukiyo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.zyakusen.tsukiyo.data.AppContainer
import io.github.zyakusen.tsukiyo.data.model.Work
import kotlinx.coroutines.launch

/**
 * 首页浏览状态：跨导航保留已加载列表、排序与分页位置。
 */
class BrowseViewModel(private val container: AppContainer) : ViewModel() {

    var sortKey by mutableStateOf(container.settingsStore.defaultSort)
        private set

    var sortAsc by mutableStateOf(false)
        private set

    var appliedSubtitlesOnly by mutableStateOf<Boolean?>(null)

    var items by mutableStateOf<List<Work>>(emptyList())
        private set

    var currentPage by mutableStateOf(1)
        private set

    var totalCount by mutableStateOf(0)
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    private val pageSize = 40

    val totalPages: Int
        get() = ((totalCount + pageSize - 1) / pageSize).coerceAtLeast(1)

    init {
        viewModelScope.launch { loadPage(1) }
    }

    fun setSort(key: String) {
        sortKey = key
    }

    fun toggleSortAsc() {
        sortAsc = !sortAsc
    }

    fun refresh() {
        viewModelScope.launch { loadPage(1) }
    }

    fun goToPage(page: Int) {
        viewModelScope.launch { loadPage(page) }
    }

    fun nextPage() {
        if (currentPage < totalPages) goToPage(currentPage + 1)
    }

    fun prevPage() {
        if (currentPage > 1) goToPage(currentPage - 1)
    }

    private suspend fun loadPage(page: Int) {
        loading = true
        error = null
        try {
            val resp = container.repository.getWorks(
                order = sortKey,
                sort = if (sortAsc) "asc" else "desc",
                page = page,
                pageSize = pageSize
            )
            items = resp.works ?: emptyList()
            totalCount = resp.pagination?.totalCount ?: 0
            currentPage = page
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }
}
