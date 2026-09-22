package com.zyakusen.tsukiyo.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.zyakusen.tsukiyo.data.api.NetworkModule
import com.zyakusen.tsukiyo.ui.MainActivity
import com.zyakusen.tsukiyo.util.currentLrcIndex
import com.zyakusen.tsukiyo.util.parseSubtitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request

data class PlayerUiState(
    val currentTrack: PlayableTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val currentIndex: Int = 0,
    val queueSize: Int = 0,
    val isBuffering: Boolean = false,
    val quality: String = "low",
    val subtitleLines: List<LrcLine> = emptyList(),
    val subtitleEnabled: Boolean = true,
    val currentSubtitle: String? = null,
    val volume: Float = 1f
)

/**
 * 播放器核心：单例 ExoPlayer + MediaSession，负责播放、状态流与字幕。
 */
object PlayerManager {

    @Volatile
    var player: ExoPlayer? = null
        private set

    @Volatile
    var mediaSession: MediaSession? = null
        private set

    @Volatile
    var currentQuality: String = "low"
        private set

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _sleepRemainingMs = MutableStateFlow<Long?>(null)
    val sleepRemainingMs: StateFlow<Long?> = _sleepRemainingMs.asStateFlow()

    private val _sleepFinishedPending = MutableStateFlow(false)
    val sleepFinishedPending: StateFlow<Boolean> = _sleepFinishedPending.asStateFlow()

    private var currentQueue: List<PlayableTrack> = emptyList()
    private var loadedSubtitleUrl: String? = null
    private var appContext: Context? = null
    private var fallbackTried = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null
    private var sleepJob: Job? = null

    /** 设置定时停止（分钟）。 */
    fun setSleepTimer(context: Context, minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _sleepRemainingMs.value = null
            return
        }
        val totalMs = minutes * 60_000L
        var remaining = totalMs
        _sleepRemainingMs.value = remaining
        sleepJob = scope.launch {
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _sleepRemainingMs.value = remaining
            }
            _sleepRemainingMs.value = null
            player?.pause()
            _sleepFinishedPending.value = true
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemainingMs.value = null
    }

    fun acknowledgeSleepFinished() {
        _sleepFinishedPending.value = false
    }

    fun sleepRemainingMinutes(): Int? =
        _sleepRemainingMs.value?.let { ((it + 59_999) / 60_000).toInt() }

    fun ensurePlayer(context: Context): ExoPlayer {
        player?.let { return it }
        appContext = context.applicationContext
        val p = ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    publish()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    publish()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    fallbackTried = false
                    publish()
                    handleTrackChange()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    handlePlaybackError()
                }
            })
        }
        player = p
        return p
    }

    fun ensureSession(context: Context): MediaSession {
        mediaSession?.let { return it }
        val p = ensurePlayer(context)
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val session = MediaSession.Builder(context, p)
            .setSessionActivity(pendingIntent)
            .build()
        mediaSession = session
        return session
    }

    fun playQueue(context: Context, queue: List<PlayableTrack>, startIndex: Int = 0, quality: String = "low") {
        currentQueue = queue
        currentQuality = quality
        loadedSubtitleUrl = null
        fallbackTried = false
        val p = ensurePlayer(context)
        ensureSession(context)
        startForegroundService(context)
        val items = queue.map { buildMediaItem(it) }
        p.setMediaItems(items, startIndex, 0L)
        p.prepare()
        p.play()
        startTicker()
        handleTrackChange()
        publish()
    }

    fun setQuality(context: Context, quality: String) {
        if (quality == currentQuality || currentQueue.isEmpty()) return
        val p = ensurePlayer(context)
        val position = p.currentPosition
        val index = p.currentMediaItemIndex.coerceIn(0, currentQueue.lastIndex)
        val wasPlaying = p.isPlaying
        currentQuality = quality
        val items = currentQueue.map { buildMediaItem(it) }
        p.setMediaItems(items, index, position)
        p.prepare()
        if (wasPlaying) p.play()
        publish()
    }

    /** 播放失败时切换到另一码率（如低码率 CDN 不可达则回退原始文件）。 */
    private fun handlePlaybackError() {
        if (fallbackTried || currentQueue.isEmpty()) {
            publish()
            return
        }
        val track = currentTrack() ?: run { publish(); return }
        val alternate = if (currentQuality == "low") "high" else "low"
        if (track.urlFor(alternate) == track.urlFor(currentQuality)) {
            publish()
            return
        }
        fallbackTried = true
        val ctx = appContext ?: run { publish(); return }
        val p = player ?: run { publish(); return }
        val position = p.currentPosition
        val index = p.currentMediaItemIndex.coerceIn(0, currentQueue.lastIndex)
        val wasPlaying = p.isPlaying
        currentQuality = alternate
        val items = currentQueue.map { buildMediaItem(it) }
        p.setMediaItems(items, index, position)
        p.prepare()
        if (wasPlaying) p.play()
        publish()
    }

    fun togglePlay(context: Context) {
        val p = ensurePlayer(context)
        if (p.isPlaying) p.pause() else p.play()
    }

    fun seekTo(context: Context, positionMs: Long) {
        val p = ensurePlayer(context)
        if (p.duration <= 0) return
        p.seekTo(positionMs)
        publish()
    }

    fun seekBy(context: Context, deltaMs: Long) {
        val p = ensurePlayer(context)
        if (p.duration <= 0) return
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0L, p.duration))
        publish()
    }

    fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0f, 1f)
        publish()
    }

    fun toggleSubtitle() {
        _uiState.value = _uiState.value.copy(subtitleEnabled = !_uiState.value.subtitleEnabled)
    }

    fun next(context: Context) {
        val p = ensurePlayer(context)
        if (p.hasNextMediaItem()) p.seekToNextMediaItem() else p.seekTo(0)
    }

    fun previous(context: Context) {
        val p = ensurePlayer(context)
        if (p.currentPosition > 5000) p.seekTo(0) else if (p.hasPreviousMediaItem()) p.seekToPreviousMediaItem()
    }

    fun stop(context: Context) {
        cancelSleepTimer()
        player?.stop()
        player?.clearMediaItems()
        currentQueue = emptyList()
        _uiState.value = PlayerUiState(volume = _uiState.value.volume)
        stopTicker()
        runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
    }

    fun release(context: Context) {
        cancelSleepTimer()
        stopTicker()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
    }

    private fun startForegroundService(context: Context) {
        runCatching {
            val intent = Intent(context, PlaybackService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun buildMediaItem(track: PlayableTrack): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.workTitle)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.urlFor(currentQuality))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun handleTrackChange() {
        val track = currentTrack()
        val url = track?.subtitleUrl
        if (url.isNullOrBlank()) {
            loadedSubtitleUrl = null
            _uiState.value = _uiState.value.copy(subtitleLines = emptyList(), currentSubtitle = null)
            return
        }
        if (url == loadedSubtitleUrl) return
        loadedSubtitleUrl = url
        scope.launch(Dispatchers.IO) {
            val content = runCatching {
                val req = Request.Builder().url(url).build()
                NetworkModule.authenticatedClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            }.getOrNull()
            val lines = if (content.isNullOrBlank()) emptyList() else parseSubtitle(content)
            _uiState.value = _uiState.value.copy(subtitleLines = lines)
        }
    }

    private fun currentTrack(): PlayableTrack? {
        val p = player ?: return null
        val idx = p.currentMediaItemIndex
        return if (idx in currentQueue.indices) currentQueue[idx] else null
    }

    private fun publish() {
        val p = player
        val current = currentTrack()
        val positionMs = p?.currentPosition ?: 0L
        val currentSubtitle = if (_uiState.value.subtitleEnabled) {
            val lines = _uiState.value.subtitleLines
            val idx = currentLrcIndex(lines, positionMs)
            if (idx >= 0) lines[idx].text else null
        } else null

        _uiState.value = _uiState.value.copy(
            currentTrack = current,
            isPlaying = p?.isPlaying == true,
            positionMs = positionMs,
            durationMs = p?.duration?.takeIf { it > 0 } ?: current?.durationMs ?: 0L,
            bufferedMs = p?.bufferedPosition ?: 0L,
            currentIndex = p?.currentMediaItemIndex ?: 0,
            queueSize = currentQueue.size,
            isBuffering = p?.playbackState == Player.STATE_BUFFERING,
            quality = currentQuality,
            currentSubtitle = currentSubtitle,
            volume = p?.volume ?: _uiState.value.volume
        )
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                publish()
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
