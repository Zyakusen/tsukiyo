package io.github.zyakusen.tsukiyo.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import io.github.zyakusen.tsukiyo.data.dao.DownloadDao
import io.github.zyakusen.tsukiyo.data.entity.DownloadItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * 全局下载队列：限制同时下载的文件数，其余排队等待。
 */
object DownloadQueue {

    const val MAX_CONCURRENT = 2

    private val mutex = Mutex()

    /** 若活跃下载未达上限，取出最早的排队项并启动。 */
    suspend fun scheduleNext(context: Context, dao: DownloadDao) {
        mutex.withLock {
            val active = dao.countByStatus(DownloadItem.STATUS_DOWNLOADING)
            if (active >= MAX_CONCURRENT) return
            val next = dao.getFirstByStatus(DownloadItem.STATUS_QUEUED) ?: return
            dao.update(next.copy(status = DownloadItem.STATUS_DOWNLOADING))
            enqueue(context, next)
        }
    }

    fun enqueue(context: Context, item: DownloadItem) {
        val input = Data.Builder()
            .putString("id", item.id)
            .putString("url", item.url)
            .putString("fileName", item.fileName)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(input)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("download_${item.id}", ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$id")
    }
}
