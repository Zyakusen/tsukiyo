package io.github.zyakusen.tsukiyo.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.github.zyakusen.tsukiyo.data.api.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

suspend fun saveImageToGallery(context: Context, url: String, fileName: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            // ① 优先复用预览（Coil）已经下载好的原始字节：同一张图通常 2-3MB，
            //    预览已经加载过就不该再联网下一遍。
            // ② 缓存未命中（图片还没加载完 / 被清理）才走网络。
            val bytes = bytesFromImageCache(context, url) ?: downloadBytes(url)
            if (bytes == null) false else writeBytesToGallery(context, bytes, fileName)
        }.getOrDefault(false)
    }

/** 从 Coil 磁盘缓存里取原图字节；未命中返回 null。 */
private fun bytesFromImageCache(context: Context, url: String): ByteArray? = runCatching {
    val request = coil.request.ImageRequest.Builder(context).data(url).build()
    val key = request.diskCacheKey ?: return null
    coil.Coil.imageLoader(context).diskCache?.get(key)?.use { snapshot ->
        snapshot.data.toFile().readBytes()
    }
}.getOrNull()

private fun downloadBytes(url: String): ByteArray? = runCatching {
    NetworkModule.downloadClient.newCall(Request.Builder().url(url).build())
        .execute().use { resp -> if (resp.isSuccessful) resp.body?.bytes() else null }
}.getOrNull()

suspend fun saveImageFileToGallery(context: Context, file: File, fileName: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@withContext false
            writeBytesToGallery(context, file.readBytes(), fileName)
        }.getOrDefault(false)
    }

/** 按扩展名推断 MIME（原来写死 image/jpeg，保存 PNG 时类型会记错）。 */
private fun mimeOf(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "bmp" -> "image/bmp"
    else -> "image/jpeg"
}

private fun writeBytesToGallery(context: Context, bytes: ByteArray, fileName: String): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeOf(fileName))
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ASMR")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        return if (uri != null) {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            true
        } else {
            false
        }
    } else {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val file = File(dir, "ASMR/$fileName")
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        return true
    }
}
