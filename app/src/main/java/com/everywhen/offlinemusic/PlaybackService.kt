package com.everywhen.offlinemusic

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.*

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao by lazy { (application as MusicApplication).database.musicDao() }
    private var lastTrackId: Long? = null
    private var lastPositionSnapshot: Long = 0

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val old = lastTrackId
                if (old != null) {
                    val positionToSave = if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) 0L else lastPositionSnapshot
                    scope.launch { runCatching { dao.savePosition(old, positionToSave) } }
                }
                lastTrackId = mediaItem?.mediaId?.toLongOrNull()
                lastPositionSnapshot = player.currentPosition.coerceAtLeast(0)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveCurrentPosition()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    lastTrackId?.let { id -> scope.launch { runCatching { dao.savePosition(id, 0L) } } }
                }
            }
        })

        session = MediaSession.Builder(this, player).build()

        scope.launch {
            while (isActive) {
                delay(5000)
                if (::player.isInitialized && player.currentMediaItem != null) {
                    lastPositionSnapshot = player.currentPosition.coerceAtLeast(0)
                    saveCurrentPosition()
                }
            }
        }
    }

    private fun saveCurrentPosition() {
        if (!::player.isInitialized) return
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val pos = player.currentPosition.coerceAtLeast(0)
        lastPositionSnapshot = pos
        scope.launch { runCatching { dao.savePosition(id, pos) } }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveCurrentPosition()
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        saveCurrentPosition()
        if (::session.isInitialized) session.release()
        if (::player.isInitialized) player.release()
        scope.cancel()
        super.onDestroy()
    }
}
