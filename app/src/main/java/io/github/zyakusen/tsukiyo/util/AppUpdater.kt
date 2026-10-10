package io.github.zyakusen.tsukiyo.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.zyakusen.tsukiyo.data.api.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * 应用内更新：下载 GitHub Release 里的 APK，再交给系统安装器。
 *
 * 比"跳浏览器"少两步（不用离开应用、不用自己找安装包），下载过程还能给出进度。
 * 安装包放在 `cacheDir/updates/`，已由 `file_paths.xml` 的 `cache-path` 授权给 FileProvider。
 */
object AppUpdater {

    private const val PROVIDER_SUFFIX = ".fileprovider"

    private fun targetFile(context: Context, version: String): File =
        File(File(context.cacheDir, "updates").apply { mkdirs() }, "Tsukiyo-$version.apk")

    /**
     * 下载安装包；[onProgress] 回调 0f–1f 的进度（拿不到总长度时不回调）。
     * 返回下载好的文件，失败返回 null。
     */
    suspend fun download(
        context: Context,
        url: String,
        version: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val target = targetFile(context, version)
            val req = Request.Builder().url(url).header("User-Agent", "tsukiyo").build()
            NetworkModule.downloadClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                val total = body.contentLength()
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = (read * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct / 100f)
                                }
                            }
                        }
                    }
                }
            }
            target.takeIf { it.length() > 0 }
        }.getOrNull()
    }

    /** 是否已允许本应用安装应用（Android 8.0+ 需要用户授权一次）。 */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** 跳到「安装未知应用」授权页。 */
    fun openInstallPermission(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 调起系统安装器。 */
    fun install(context: Context, file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + PROVIDER_SUFFIX, file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        true
    }.getOrDefault(false)
}
