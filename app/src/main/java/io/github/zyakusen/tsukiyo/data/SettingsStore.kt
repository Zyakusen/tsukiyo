package io.github.zyakusen.tsukiyo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用设置，基于 SharedPreferences 存储。
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_BASE_URL = "https://api.asmr-200.com"
        val MIRRORS = listOf(
            "https://api.asmr-200.com",
            "https://api.asmr.one",
            "https://api.asmr-100.com",
            "https://api.asmr-300.com"
        )
        const val QUALITY_LOW = "low"
        const val QUALITY_HIGH = "high"

        // 效果音偏好：三态（替代旧的布尔 sePreference）
        const val SE_MODE_ANY = "any"
        const val SE_MODE_WITH = "with"
        const val SE_MODE_WITHOUT = "without"

        /**
         * 读取效果音偏好模式，并兼容旧键。
         *
         * 旧开关 `sePreference` 在旧版里**几乎不生效**（实测 405 部作品中 94.8% 的结果
         * 不因它而改变），所以无论它当时是开是关，都迁移为 `with`；
         * 否则升级后新的 SE 逻辑会完全不起作用，用户会以为"没修好"。
         * 需要"不限"的用户可以在设置里显式选择。
         */
        fun readSeMode(prefs: android.content.SharedPreferences): String {
            prefs.getString("seMode", null)?.let { return it }
            return SE_MODE_WITH
        }

        /**
         * 音频类型偏好顺序的可选集合，**顺序取自 asmr.one 网站设置的默认值**
         * （`audioTypePreference: ["mp3","flac","wav","opus","m4a","aac"]`），
         * 这样默认行为与网页端一致：优先 mp3（体积小、起播快），再考虑无损。
         * 与网站的另一处一致：网站上同一扩展名内是**按码率从高到低**挑（我们由智能路径的格式比较承担）。
         */
        val AUDIO_TYPES = listOf("mp3", "flac", "wav", "opus", "m4a", "aac")
        val DEFAULT_AUDIO_ORDER = AUDIO_TYPES.joinToString(",")
    }

    private val _state = MutableStateFlow(SettingsState.load(prefs))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    var baseUrl: String
        get() = prefs.getString("baseUrl", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            prefs.edit().putString("baseUrl", value).apply()
            _state.value = _state.value.copy(baseUrl = value)
        }

    var audioQuality: String
        get() = prefs.getString("audioQuality", QUALITY_LOW) ?: QUALITY_LOW
        set(value) {
            prefs.edit().putString("audioQuality", value).apply()
            _state.value = _state.value.copy(audioQuality = value)
        }

    var showNsfw: Boolean
        get() = prefs.getBoolean("showNsfw", true)
        set(value) {
            prefs.edit().putBoolean("showNsfw", value).apply()
            _state.value = _state.value.copy(showNsfw = value)
        }

    var subtitlesOnly: Boolean
        get() = prefs.getBoolean("subtitlesOnly", false)
        set(value) {
            prefs.edit().putBoolean("subtitlesOnly", value).apply()
            _state.value = _state.value.copy(subtitlesOnly = value)
        }

    var defaultSort: String
        get() = prefs.getString("defaultSort", "release") ?: "release"
        set(value) {
            prefs.edit().putString("defaultSort", value).apply()
            _state.value = _state.value.copy(defaultSort = value)
        }

    var tagLanguage: String
        get() = prefs.getString("tagLanguage", "zh-cn") ?: "zh-cn"
        set(value) {
            prefs.edit().putString("tagLanguage", value).apply()
            _state.value = _state.value.copy(tagLanguage = value)
        }

    var showPopularAtHome: Boolean
        get() = prefs.getBoolean("showPopularAtHome", true)
        set(value) {
            prefs.edit().putBoolean("showPopularAtHome", value).apply()
            _state.value = _state.value.copy(showPopularAtHome = value)
        }

    var showRecommendAtHome: Boolean
        get() = prefs.getBoolean("showRecommendAtHome", true)
        set(value) {
            prefs.edit().putBoolean("showRecommendAtHome", value).apply()
            _state.value = _state.value.copy(showRecommendAtHome = value)
        }

    // 快进 30s / 后退 5s：与 asmr.one 网站设置默认值一致（forwardSeekTime=30、rewindSeekTime=5）
    var forwardSeekSeconds: Int
        get() = prefs.getInt("forwardSeekSeconds", 30)
        set(value) {
            prefs.edit().putInt("forwardSeekSeconds", value).apply()
            _state.value = _state.value.copy(forwardSeekSeconds = value)
        }

    var rewindSeekSeconds: Int
        get() = prefs.getInt("rewindSeekSeconds", 5)
        set(value) {
            prefs.edit().putInt("rewindSeekSeconds", value).apply()
            _state.value = _state.value.copy(rewindSeekSeconds = value)
        }

    var audioTypeOrder: List<String>
        get() {
            val raw = prefs.getString("audioTypeOrder", DEFAULT_AUDIO_ORDER) ?: DEFAULT_AUDIO_ORDER
            val order = raw.split(",").filter { it.isNotBlank() }
            return if (order.isEmpty()) AUDIO_TYPES else order
        }
        set(value) {
            prefs.edit().putString("audioTypeOrder", value.joinToString(",")).apply()
            _state.value = _state.value.copy(audioTypeOrder = value)
        }

    var smartPathEnabled: Boolean
        get() = prefs.getBoolean("smartPathEnabled", true)
        set(value) {
            prefs.edit().putBoolean("smartPathEnabled", value).apply()
            _state.value = _state.value.copy(smartPathEnabled = value)
        }

    var seMode: String
        get() = readSeMode(prefs)
        set(value) {
            prefs.edit().putString("seMode", value).apply()
            _state.value = _state.value.copy(seMode = value)
        }

    var themeMode: String
        get() = prefs.getString("themeMode", "system") ?: "system"
        set(value) {
            prefs.edit().putString("themeMode", value).apply()
            _state.value = _state.value.copy(themeMode = value)
        }

    var proxyEnabled: Boolean
        get() = prefs.getBoolean("proxyEnabled", false)
        set(value) {
            prefs.edit().putBoolean("proxyEnabled", value).apply()
            _state.value = _state.value.copy(proxyEnabled = value)
        }

    var proxyProtocol: String
        get() = prefs.getString("proxyProtocol", "http") ?: "http"
        set(value) {
            prefs.edit().putString("proxyProtocol", value).apply()
            _state.value = _state.value.copy(proxyProtocol = value)
        }

    var proxyServer: String
        get() = prefs.getString("proxyServer", "") ?: ""
        set(value) {
            prefs.edit().putString("proxyServer", value).apply()
            _state.value = _state.value.copy(proxyServer = value)
        }

    var proxyPort: Int
        get() = prefs.getInt("proxyPort", 0)
        set(value) {
            prefs.edit().putInt("proxyPort", value).apply()
            _state.value = _state.value.copy(proxyPort = value)
        }

    var proxyUsername: String
        get() = prefs.getString("proxyUsername", "") ?: ""
        set(value) {
            prefs.edit().putString("proxyUsername", value).apply()
            _state.value = _state.value.copy(proxyUsername = value)
        }

    var proxyPassword: String
        get() = prefs.getString("proxyPassword", "") ?: ""
        set(value) {
            prefs.edit().putString("proxyPassword", value).apply()
            _state.value = _state.value.copy(proxyPassword = value)
        }

    var exportDirUri: String
        get() = prefs.getString("exportDirUri", "") ?: ""
        set(value) {
            prefs.edit().putString("exportDirUri", value).apply()
            _state.value = _state.value.copy(exportDirUri = value)
        }

    var lastMirrorCheckAt: Long
        get() = prefs.getLong("lastMirrorCheckAt", 0)
        set(value) {
            prefs.edit().putLong("lastMirrorCheckAt", value).apply()
        }

    /**
     * 用户点了「不再提示」的那个版本号。
     *
     * 语义是**只忽略这一个版本**：判断用的是"最新版本 != 已忽略版本"，
     * 所以忽略 1.3.2 之后出了 1.3.3 仍会提示；更新到 1.3.2 之后自然也不再提示。
     */
    var ignoredUpdateVersion: String
        get() = prefs.getString("ignoredUpdateVersion", "") ?: ""
        set(value) {
            prefs.edit().putString("ignoredUpdateVersion", value).apply()
        }
}

data class SettingsState(
    val baseUrl: String = SettingsStore.DEFAULT_BASE_URL,
    val audioQuality: String = SettingsStore.QUALITY_LOW,
    val showNsfw: Boolean = true,
    val subtitlesOnly: Boolean = false,
    val defaultSort: String = "release",
    val tagLanguage: String = "zh-cn",
    val showPopularAtHome: Boolean = true,
    val showRecommendAtHome: Boolean = true,
    val forwardSeekSeconds: Int = 30,
    val rewindSeekSeconds: Int = 5,
    val audioTypeOrder: List<String> = SettingsStore.AUDIO_TYPES,
    val smartPathEnabled: Boolean = true,
    val seMode: String = SettingsStore.SE_MODE_WITH,
    val themeMode: String = "system",
    val proxyEnabled: Boolean = false,
    val proxyProtocol: String = "http",
    val proxyServer: String = "",
    val proxyPort: Int = 0,
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    val exportDirUri: String = ""
) {
    companion object {
        fun load(prefs: android.content.SharedPreferences): SettingsState {
            val rawOrder = prefs.getString("audioTypeOrder", SettingsStore.DEFAULT_AUDIO_ORDER) ?: SettingsStore.DEFAULT_AUDIO_ORDER
            val order = rawOrder.split(",").filter { it.isNotBlank() }.ifEmpty { SettingsStore.AUDIO_TYPES }
            return SettingsState(
                baseUrl = prefs.getString("baseUrl", SettingsStore.DEFAULT_BASE_URL) ?: SettingsStore.DEFAULT_BASE_URL,
                audioQuality = prefs.getString("audioQuality", SettingsStore.QUALITY_LOW) ?: SettingsStore.QUALITY_LOW,
                showNsfw = prefs.getBoolean("showNsfw", true),
                subtitlesOnly = prefs.getBoolean("subtitlesOnly", false),
                defaultSort = prefs.getString("defaultSort", "release") ?: "release",
                tagLanguage = prefs.getString("tagLanguage", "zh-cn") ?: "zh-cn",
                showPopularAtHome = prefs.getBoolean("showPopularAtHome", true),
                showRecommendAtHome = prefs.getBoolean("showRecommendAtHome", true),
                forwardSeekSeconds = prefs.getInt("forwardSeekSeconds", 30),
                rewindSeekSeconds = prefs.getInt("rewindSeekSeconds", 5),
                audioTypeOrder = order,
                smartPathEnabled = prefs.getBoolean("smartPathEnabled", true),
                seMode = SettingsStore.readSeMode(prefs),
                themeMode = prefs.getString("themeMode", "system") ?: "system",
                proxyEnabled = prefs.getBoolean("proxyEnabled", false),
                proxyProtocol = prefs.getString("proxyProtocol", "http") ?: "http",
                proxyServer = prefs.getString("proxyServer", "") ?: "",
                proxyPort = prefs.getInt("proxyPort", 0),
                proxyUsername = prefs.getString("proxyUsername", "") ?: "",
                proxyPassword = prefs.getString("proxyPassword", "") ?: "",
                exportDirUri = prefs.getString("exportDirUri", "") ?: ""
            )
        }
    }
}
