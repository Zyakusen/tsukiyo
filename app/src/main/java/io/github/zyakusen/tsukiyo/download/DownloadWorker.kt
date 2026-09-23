package io.github.zyakusen.tsukiyo.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.zyakusen.tsukiyo.data.AppDatabase
import io.github.zyakusen.tsukiyo.data.api.NetworkModule
import io.github.zyakusen.tsukiyo.data.entity.DownloadItem
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * 下载任务：在后台执行实际的音频下载并更新进度。
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val url = inputData.getString("url") ?: return Result.failure()
        val fileName = inputData.getString("fileName") ?: return Result.failure()

        val dao = AppDatabase.getInstance(applicationContext).downloadDao()
        val item = dao.get(id) ?: return Result.failure()

        val dir = File(applicationContext.getExternalFilesDir(null), "asmr_downloads").apply { mkdirs() }
        val target = File(dir, fileName)

        dao.update(item.copy(status = DownloadItem.STATUS_DOWNLOADING, progress = 0f))

        return try {
            val request = Request.Builder().url(url).build()
            NetworkModule.downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("空响应体")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        var lastUpdate = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copied += read
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 300) {
                                lastUpdate = now
                                val progress = if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else 0f
                                dao.update(item.copy(status = DownloadItem.STATUS_DOWNLOADING, progress = progress))
                            }
                        }
                    }
                }
            }
            dao.update(
                item.copy(
                    status = DownloadItem.STATUS_DONE,
                    progress = 1f,
                    localPath = target.absolutePath,
                    sizeBytes = target.length()
                )
            )
            Result.success()
        } catch (e: Exception) {
            dao.update(item.copy(status = DownloadItem.STATUS_FAILED, progress = item.progress))
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
