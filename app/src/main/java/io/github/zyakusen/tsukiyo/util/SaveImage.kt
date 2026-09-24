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
            val bytes = NetworkModule.downloadClient.newCall(Request.Builder().url(url).build())
                .execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.bytes() else null
                } ?: return@withContext false
            writeBytesToGallery(context, bytes, fileName)
        }.getOrDefault(false)
    }

suspend fun saveImageFileToGallery(context: Context, file: File, fileName: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@withContext false
            writeBytesToGallery(context, file.readBytes(), fileName)
        }.getOrDefault(false)
    }

private fun writeBytesToGallery(context: Context, bytes: ByteArray, fileName: String): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
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
