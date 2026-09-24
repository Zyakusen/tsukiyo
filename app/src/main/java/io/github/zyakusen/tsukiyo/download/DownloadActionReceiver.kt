package io.github.zyakusen.tsukiyo.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.zyakusen.tsukiyo.AsmrApplication

/**
 * 下载通知栏动作接收器：处理 暂停 / 继续 / 取消。
 */
class DownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? AsmrApplication ?: return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val manager = app.container.downloadManager
        app.container.launchIO {
            when (intent.action) {
                ACTION_PAUSE -> manager.pause(id)
                ACTION_RESUME -> manager.resume(id)
                ACTION_CANCEL -> manager.deleteById(id)
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "io.github.zyakusen.tsukiyo.download.PAUSE"
        const val ACTION_RESUME = "io.github.zyakusen.tsukiyo.download.RESUME"
        const val ACTION_CANCEL = "io.github.zyakusen.tsukiyo.download.CANCEL"
        const val EXTRA_ID = "id"
    }
}
