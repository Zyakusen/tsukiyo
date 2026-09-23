package io.github.zyakusen.tsukiyo.data.api

import io.github.zyakusen.tsukiyo.data.AuthManager
import io.github.zyakusen.tsukiyo.data.SettingsState
import okhttp3.Cache
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * 网络层构建：OkHttp + Retrofit。
 * 所有 API 请求自动附加 Origin/Referer/UA 与认证 Token，支持代理。
 */
object NetworkModule {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    @Volatile
    var api: AsmrApi? = null
        private set

    @Volatile
    var authenticatedClient: OkHttpClient = OkHttpClient()
        private set

    @Volatile
    var downloadClient: OkHttpClient = buildOkHttp(null)
        private set

    fun build(baseUrl: String, authManager: AuthManager, settings: SettingsState? = null, cacheDir: File? = null, debug: Boolean = false): AsmrApi {
        val builder = baseOkHttpBuilder(settings)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Origin", "https://www.asmr.one")
                    .header("Referer", "https://www.asmr.one/")
                    .header("User-Agent", USER_AGENT)
                    .apply {
                        authManager.token?.let { header("Authorization", "Bearer $it") }
                    }
                    .build()
                chain.proceed(request)
            }

        if (cacheDir != null) {
            runCatching { builder.cache(Cache(cacheDir, 50L * 1024 * 1024)) }
            builder.addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (chain.request().method == "GET") {
                    response.newBuilder()
                        .header("Cache-Control", "public, max-age=300")
                        .build()
                } else {
                    response
                }
            }
        }

        if (debug) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
        }

        val client = builder.build()

        val retrofit = Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(baseUrl))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        authenticatedClient = client
        val created = retrofit.create(AsmrApi::class.java)
        api = created
        return created
    }

    fun rebuildDownloadClient(settings: SettingsState?) {
        downloadClient = buildOkHttp(settings)
    }

    private fun baseOkHttpBuilder(settings: SettingsState?): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        applyProxy(builder, settings)
        return builder
    }

    private fun buildOkHttp(settings: SettingsState?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        applyProxy(builder, settings)
        return builder.build()
    }

    private fun applyProxy(builder: OkHttpClient.Builder, settings: SettingsState?) {
        if (settings == null || !settings.proxyEnabled || settings.proxyServer.isBlank()) return
        runCatching {
            val type = if (settings.proxyProtocol == "socks") Proxy.Type.SOCKS else Proxy.Type.HTTP
            builder.proxy(Proxy(type, InetSocketAddress(settings.proxyServer, settings.proxyPort.coerceIn(1, 65535))))
            if (settings.proxyUsername.isNotBlank()) {
                val credential = Credentials.basic(settings.proxyUsername, settings.proxyPassword)
                builder.proxyAuthenticator { _, response ->
                    response.request.newBuilder().header("Proxy-Authorization", credential).build()
                }
            }
        }
    }

    fun ensureTrailingSlash(url: String): String = if (url.endsWith("/")) url else "$url/"
}
