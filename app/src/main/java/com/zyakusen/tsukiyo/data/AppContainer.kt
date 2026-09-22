package com.zyakusen.tsukiyo.data

import android.content.Context
import com.zyakusen.tsukiyo.data.api.AsmrApi
import com.zyakusen.tsukiyo.data.api.NetworkModule
import com.zyakusen.tsukiyo.data.repository.AsmrRepository
import com.zyakusen.tsukiyo.download.DownloadManager
import com.zyakusen.tsukiyo.player.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cacheDir: File = File(appContext.cacheDir, "http_cache")

    /** 首页「回到顶部」信号。 */
    val homeScrollToTopSignal = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun scrollHomeToTop() {
        homeScrollToTopSignal.value++
    }

    init {
        applyNetworkConfig()
        repository = AsmrRepository({ api }, authManager, settingsStore)
        downloadManager = DownloadManager(context, database.downloadDao())
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

    /** 启动时根据测速选择延迟最低的可用镜像。 */
    fun selectBestMirror() {
        scope.launch {
            val results = SettingsStore.MIRRORS.map { mirror ->
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
}
