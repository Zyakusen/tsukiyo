package io.github.zyakusen.tsukiyo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import io.github.zyakusen.tsukiyo.data.AppContainer

class AsmrApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    "asmr_playback",
                    getString(R.string.media_playback_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    "asmr_download",
                    getString(R.string.download_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
}
