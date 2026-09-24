package io.github.zyakusen.tsukiyo.data

import android.content.Context
import io.github.zyakusen.tsukiyo.data.api.AsmrApi
import io.github.zyakusen.tsukiyo.data.api.NetworkModule
import io.github.zyakusen.tsukiyo.data.repository.AsmrRepository
import io.github.zyakusen.tsukiyo.download.DownloadManager
import io.github.zyakusen.tsukiyo.player.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 应用级依赖容器。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settingsStore = SettingsStore(context)
    val authManager = AuthManager(context)
    val database = AppDatabase.getInstance(context)

    @Volatile
    lateinit var api: AsmrApi

    val repository: AsmrRepository
    val downloadManager: DownloadManager
    val reviewStore: ReviewStore
    val historyStore: HistoryStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cacheDir: File = File(appContext.cacheDir, "http_cache")

    /** 首页「回到顶部」信号。 */
    val homeScrollToTopSignal = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun scrollHomeToTop() {
        homeScrollToTopSignal.value++
    }

    /** 在后台 IO 作用域执行挂起任务（供广播接收器等非 UI 入口使用）。 */
    fun launchIO(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    init {
        applyNetworkConfig()
        repository = AsmrRepository({ api }, authManager, settingsStore, MetadataCache(context), WorkCache(context))
        downloadManager = DownloadManager(context, database.downloadDao())
        reviewStore = ReviewStore(database.reviewDao())
        historyStore = HistoryStore(database.historyDao())
    }

    private fun applyNetworkConfig() {
        val settings = settingsStore.state.value
        api = NetworkModule.build(settingsStore.baseUrl, authManager, settings, cacheDir)
        NetworkModule.rebuildDownloadClient(settings)
    }

    /** 切换 API 镜像并重建网络层。 */
    fun switchBaseUrl(url: String) {
        settingsStore.baseUrl = url
        applyNetworkConfig()
    }

    /** 重新登录后刷新认证相关的网络层（无操作，保留供调用）。 */
    fun applyProxySettings() {
        applyNetworkConfig()
    }

    /** 清理缓存（HTTP 缓存 + 图片缓存），返回释放的字节数。 */
    fun clearCache(context: Context): Long {
        var freed = runCatching {
            cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
        runCatching { cacheDir.deleteRecursively() }
        runCatching {
            val loader = coil.Coil.imageLoader(context.applicationContext)
            loader.diskCache?.clear()
            loader.memoryCache?.clear()
        }
        applyNetworkConfig()
        return freed
    }

    /** 启动时根据测速选择延迟最低的可用镜像（并行探测，7 天内不重复）。 */
    fun selectBestMirror() {
        val now = System.currentTimeMillis()
        if (now - settingsStore.lastMirrorCheckAt < 7L * 24 * 3600 * 1000) return
        settingsStore.lastMirrorCheckAt = now
        scope.launch {
            val results = SettingsStore.MIRRORS.map { mirror ->
                async {
                    val latency = withContext(Dispatchers.IO) {
                        runCatching {
                            val start = System.currentTimeMillis()
                            val req = okhttp3.Request.Builder().url("${mirror}/api/health").build()
                            NetworkModule.downloadClient.newCall(req).execute().use {
                                if (it.isSuccessful) System.currentTimeMillis() - start else Long.MAX_VALUE
                            }
                        }.getOrDefault(Long.MAX_VALUE)
                    }
                    mirror to latency
                }
            }.awaitAll()
            val best = results.filter { it.second < Long.MAX_VALUE }.minByOrNull { it.second }
            if (best != null) {
                switchBaseUrl(best.first)
            }
        }
    }

    /** 确保已登录（游客自动登录）。 */
    suspend fun ensureGuestLogin(): Boolean {
        if (authManager.token != null) return true
        return repository.guestLogin()
    }

    /** 启动时后台同步收藏页数据（评分/评论/进度）到本地缓存，最多拉取最近几页。 */
    fun syncReviewsInBackground() {
        if (!authManager.isRealUser) return
        scope.launch {
            runCatching {
                var page = 1
                val pageSize = 50
                while (page <= 5) {
                    val resp = repository.getReviews(order = "updated_at", sort = "desc", page = page, pageSize = pageSize)
                    val works = resp.works ?: emptyList()
                    works.forEach { w -> w.id?.let { id -> reviewStore.saveAll(id, w.userRating, w.reviewText, w.progress) } }
                    if (works.size < pageSize) break
                    page++
                }
            }
        }
    }
}
