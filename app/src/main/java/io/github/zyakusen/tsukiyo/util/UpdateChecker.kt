package io.github.zyakusen.tsukiyo.util

import android.content.Context
import io.github.zyakusen.tsukiyo.data.api.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** 检查更新拿到的信息：版本号、发行说明（Markdown 文本）、APK 直链（拿不到就退回发行页）。 */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val htmlUrl: String
)

/**
 * 查 GitHub 最新**正式**发行版。
 *
 * 用 `/releases/latest`：它只返回正式版（草稿与预发行版会被跳过），
 * 所以发版时**不能**勾 pre-release，否则客户端永远看不到更新。
 * 任何失败都返回 null（启动时静默、设置页里再提示失败）。
 */
suspend fun fetchLatestRelease(): UpdateInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val req = Request.Builder()
            .url("https://api.github.com/repos/Zyakusen/tsukiyo/releases/latest")
            .header("User-Agent", "tsukiyo")
            .build()
        NetworkModule.downloadClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else {
                val json = JSONObject(resp.body?.string() ?: "")
                val tag = json.optString("tag_name").removePrefix("v")
                if (tag.isBlank()) null else {
                    // Release 里的 APK 资产直链（应用内下载用）
                    var apk = ""
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            if (a.optString("name").endsWith(".apk", true)) {
                                apk = a.optString("browser_download_url")
                                break
                            }
                        }
                    }
                    UpdateInfo(
                        version = tag,
                        notes = json.optString("body"),
                        apkUrl = apk,
                        htmlUrl = json.optString("html_url")
                    )
                }
            }
        }
    }.getOrNull()
}

/** 按「主.次.修订」逐段数值比较，a > b 返回正数、相等 0、小于负数。每段只取开头数字。 */
fun compareVersions(a: String, b: String): Int {
    val pa = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val pb = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return 0
}

/** 当前安装的版本名（如 "1.3.2"）。 */
fun currentVersionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0.0"
