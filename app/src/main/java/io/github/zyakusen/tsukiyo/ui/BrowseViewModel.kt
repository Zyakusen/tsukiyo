package io.github.zyakusen.tsukiyo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.zyakusen.tsukiyo.data.AppContainer
import io.github.zyakusen.tsukiyo.data.HomeFeedCache
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
     * 最近一次**请求失败**的页码。
     *
     * 以前失败后点「重试」走的是 `refresh()`（固定第 1 页），于是"在第 N 页翻页/重试却跳回第 1 页"。
     * 现在记下失败的页码，重试就回到那一页。
     */
    var failedPage by mutableStateOf<Int?>(null)
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

    // 两行的加载 / 失败状态：让首页能"先占位、加载好再替换"，而不是没数据就整行不显示。
    var popularLoading by mutableStateOf(false)
        private set
    var recommendLoading by mutableStateOf(false)
        private set
    var popularError by mutableStateOf<String?>(null)
        private set
    var recommendError by mutableStateOf<String?>(null)
        private set

    private val pageSize = 40

    val totalPages: Int
        get() = ((totalCount + pageSize - 1) / pageSize).coerceAtLeast(1)

    init {
        viewModelScope.launch {
            // 冷启动先用上次缓存的两行占位（磁盘读取放到协程里，不占主线程）
            val cachedPopular = container.homeFeedCache.get(HomeFeedCache.KEY_POPULAR)
            if (cachedPopular.isNotEmpty()) popularWorks = cachedPopular
            val cachedRecommend = container.homeFeedCache.get(HomeFeedCache.KEY_RECOMMEND)
            if (cachedRecommend.isNotEmpty()) recommendWorks = cachedRecommend
        }
        viewModelScope.launch { loadPage(1) }
    }

    fun setSort(key: String) {
        sortKey = key
    }

    fun toggleSortAsc() {
        sortAsc = !sortAsc
    }

    /**
     * 加载热门行。已有数据（含上次缓存）时**保留旧列表**，请求回来再替换 —— 这样占位行不会先清空再填充。
     */
    fun loadPopular(force: Boolean = false) {
        if (!force && popularWorks.isNotEmpty()) return
        viewModelScope.launch {
            popularLoading = true
            popularError = null
            runCatching { container.repository.popularWorks(pageSize = 20).works ?: emptyList() }
                .onSuccess {
                    popularWorks = it
                    container.homeFeedCache.put(HomeFeedCache.KEY_POPULAR, it)
                }
                .onFailure { popularError = it.message ?: "加载失败" }
            popularLoading = false
        }
    }

    fun clearPopular() {
        popularWorks = emptyList()
        popularError = null
        popularLoading = false
    }

    fun loadRecommend(enabled: Boolean, force: Boolean = false) {
        if (!enabled) {
            recommendWorks = emptyList()
            recommendError = null
            recommendLoading = false
            return
        }
        if (!force && recommendWorks.isNotEmpty()) return
        viewModelScope.launch {
            recommendLoading = true
            recommendError = null
            runCatching { container.repository.recommendWorks(pageSize = 20).works ?: emptyList() }
                .onSuccess {
                    recommendWorks = it
                    container.homeFeedCache.put(HomeFeedCache.KEY_RECOMMEND, it)
                }
                .onFailure { recommendError = it.message ?: "加载失败" }
            recommendLoading = false
        }
    }

    /** 回到第 1 页重新加载（切换排序时用）。 */
    fun refresh() {
        viewModelScope.launch { loadPage(1) }
    }

    /** 刷新**当前页**（顶栏刷新按钮）：不再把用户踢回第 1 页。 */
    fun refreshCurrentPage() {
        viewModelScope.launch { loadPage(currentPage) }
    }

    /** 重试失败的那一页；没有失败记录时重试当前页。 */
    fun retry() {
        viewModelScope.launch { loadPage(failedPage ?: currentPage) }
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
            failedPage = null
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
            failedPage = page
        } finally {
            loading = false
        }
    }
}
