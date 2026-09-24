package io.github.zyakusen.tsukiyo.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.zyakusen.tsukiyo.data.AppDatabase
import io.github.zyakusen.tsukiyo.data.api.NetworkModule
import io.github.zyakusen.tsukiyo.data.entity.DownloadItem
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/**
 * 下载任务：带前台通知、进度条、暂停/取消，支持 Range 断点续传。
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
        val partFile = File(dir, "$fileName.part")
        val target = File(dir, fileName)

        // 断点偏移：以磁盘 .part 为准，但截断到 DB 记录值，避免暂停瞬间计数偏差导致的重复字节。
        var offset = if (partFile.exists()) partFile.length() else 0L
        val recorded = item.downloadedBytes
        if (offset > recorded) {
            offset = recorded
            RandomAccessFile(partFile, "rw").use { it.setLength(recorded) }
        }

        var copied = offset
        val notificationId = item.id.hashCode() and 0x7FFFFFFF

        setForeground(createForegroundInfo(item, notificationId, progress = -1f))
        dao.update(item.copy(status = DownloadItem.STATUS_DOWNLOADING, downloadedBytes = offset))

        return try {
            val builder = Request.Builder().url(url)
            if (offset > 0) builder.header("Range", "bytes=$offset-")
            NetworkModule.downloadClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                // 服务端忽略 Range 返回 200：截断重来
                if (offset > 0 && response.code != 206) {
                    offset = 0
                    copied = 0
                    RandomAccessFile(partFile, "rw").use { it.setLength(0) }
                }
                val body = response.body ?: throw IOException("空响应体")
                val total = if (response.code == 206) offset + body.contentLength() else body.contentLength()
                FileOutputStream(partFile, true).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var lastUpdate = 0L
                        while (true) {
                            if (isStopped) {
                                val p = if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else 0f
                                dao.updateProgress(id, p, copied)
                                dao.update(item.copy(status = DownloadItem.STATUS_PAUSED, downloadedBytes = copied))
                                DownloadQueue.scheduleNext(applicationContext, dao)
                                return Result.success()
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copied += read
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 300) {
                                lastUpdate = now
                                val progress = if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else 0f
                                dao.updateProgress(id, progress, copied)
                                setForeground(createForegroundInfo(item, notificationId, progress))
                            }
                        }
                    }
                }
            }
            if (!partFile.renameTo(target)) {
                partFile.copyTo(target, overwrite = true)
                partFile.delete()
            }
            dao.update(
                item.copy(
                    status = DownloadItem.STATUS_DONE,
                    progress = 1f,
                    localPath = target.absolutePath,
                    sizeBytes = target.length(),
                    downloadedBytes = 0L
                )
            )
            DownloadQueue.scheduleNext(applicationContext, dao)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            dao.update(item.copy(status = DownloadItem.STATUS_PAUSED, downloadedBytes = copied))
            DownloadQueue.scheduleNext(applicationContext, dao)
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 8) {
                dao.updateDownloadedBytes(id, copied)
                Result.retry()
            } else {
                dao.update(item.copy(status = DownloadItem.STATUS_FAILED, downloadedBytes = copied))
                DownloadQueue.scheduleNext(applicationContext, dao)
                Result.failure()
            }
        }
    }

    private fun createForegroundInfo(item: DownloadItem, notificationId: Int, progress: Float): ForegroundInfo {
        val indeterminate = progress < 0f
        val percent = if (indeterminate) 0 else (progress * 100).toInt().coerceIn(0, 100)

        val pause = PendingIntent.getBroadcast(
            applicationContext,
            notificationId,
            actionIntent(DownloadActionReceiver.ACTION_PAUSE, item.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getBroadcast(
            applicationContext,
            notificationId + 1,
            actionIntent(DownloadActionReceiver.ACTION_CANCEL, item.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, "asmr_download")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(item.title)
            .setContentText(if (indeterminate) "下载中…" else "下载中 $percent%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, indeterminate)
            .addAction(0, "暂停", pause)
            .addAction(0, "取消", cancel)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, builder.build())
        }
    }

    private fun actionIntent(action: String, id: String): Intent =
        Intent(applicationContext, DownloadActionReceiver::class.java)
            .setAction(action)
            .putExtra(DownloadActionReceiver.EXTRA_ID, id)
}
