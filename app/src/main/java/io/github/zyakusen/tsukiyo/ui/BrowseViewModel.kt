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

    /**
     * 首页顶部「热门作品」行。
     *
     * 放在 ViewModel 里而不是 Composable 的 `remember` 里：从作品详情页返回时首页会被重建，
     * `remember` 的列表会丢失、顶部瞬间少一行，而 `rememberLazyGridState()` 恢复的是**绝对下标**，
     * 于是视觉上会"往下错一个作品"（有推荐行时错两个）。放在 ViewModel 里则返回首帧就是完整列表。
     */
    var popularWorks by mutableStateOf<List<Work>>(emptyList())
        private set

    /** 首页顶部「推荐作品」行。同上，必须跨导航保留。 */
    var recommendWorks by mutableStateOf<List<Work>>(emptyList())
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

    fun loadPopular(force: Boolean = false) {
        if (!force && popularWorks.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { container.repository.popularWorks(pageSize = 20).works ?: emptyList() }
                .onSuccess { popularWorks = it }
        }
    }

    fun clearPopular() {
        popularWorks = emptyList()
    }

    fun loadRecommend(enabled: Boolean, force: Boolean = false) {
        if (!enabled) {
            recommendWorks = emptyList()
            return
        }
        if (!force && recommendWorks.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { container.repository.recommendWorks(pageSize = 20).works ?: emptyList() }
                .onSuccess { recommendWorks = it }
        }
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
