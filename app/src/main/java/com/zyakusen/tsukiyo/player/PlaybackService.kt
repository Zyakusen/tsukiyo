package com.zyakusen.tsukiyo.player

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * 媒体播放服务：支持后台播放与系统通知栏控制。
 */
class PlaybackService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()
        val session = PlayerManager.ensureSession(this)
        addSession(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return PlayerManager.mediaSession
    }

    override fun onDestroy() {
        PlayerManager.release(this)
        super.onDestroy()
    }
}
