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
        val AUDIO_TYPES = listOf("flac", "wav", "opus", "m4a", "aac", "mp3")
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

    var forwardSeekSeconds: Int
        get() = prefs.getInt("forwardSeekSeconds", 15)
        set(value) {
            prefs.edit().putInt("forwardSeekSeconds", value).apply()
            _state.value = _state.value.copy(forwardSeekSeconds = value)
        }

    var rewindSeekSeconds: Int
        get() = prefs.getInt("rewindSeekSeconds", 15)
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

    var sePreference: Boolean
        get() = prefs.getBoolean("sePreference", false)
        set(value) {
            prefs.edit().putBoolean("sePreference", value).apply()
            _state.value = _state.value.copy(sePreference = value)
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
    val forwardSeekSeconds: Int = 15,
    val rewindSeekSeconds: Int = 15,
    val audioTypeOrder: List<String> = SettingsStore.AUDIO_TYPES,
    val smartPathEnabled: Boolean = true,
    val sePreference: Boolean = false,
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
                forwardSeekSeconds = prefs.getInt("forwardSeekSeconds", 15),
                rewindSeekSeconds = prefs.getInt("rewindSeekSeconds", 15),
                audioTypeOrder = order,
                smartPathEnabled = prefs.getBoolean("smartPathEnabled", true),
                sePreference = prefs.getBoolean("sePreference", false),
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
