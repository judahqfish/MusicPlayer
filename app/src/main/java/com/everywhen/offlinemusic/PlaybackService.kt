package com.everywhen.offlinemusic

import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    // ExoPlayer must only be touched from its application thread (main).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dao by lazy { (application as MusicApplication).database.musicDao() }
    private val prefs by lazy { PlayerPreferences(this) }
    private var lastTrackId: Long? = null
    private var lastPositionSnapshot: Long = 0
    private var amplifierDb: Float = 0f
    private var enhancer: LoudnessEnhancer? = null
    private var enhancerSessionId: Int = -1

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
                    persistPosition(old, positionToSave)
                }
                lastTrackId = mediaItem?.mediaId?.toLongOrNull()
                lastPositionSnapshot = player.currentPosition.coerceAtLeast(0)
                updateAmplifier()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveCurrentPosition()
                updateAmplifier()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    lastTrackId?.let { persistPosition(it, 0L) }
                }
                updateAmplifier()
            }
        })

        session = MediaSession.Builder(this, player).build()

        scope.launch {
            prefs.amplifierDb.collectLatest { db ->
                amplifierDb = db.coerceIn(0f, 12f)
                updateAmplifier()
            }
        }

        scope.launch {
            while (isActive) {
                delay(1000)
                updateAmplifier()
            }
        }

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

    private fun updateAmplifier() {
        if (!::player.isInitialized) return
        val sessionId = runCatching { player.audioSessionId }.getOrDefault(-1)
        if (sessionId <= 0) return

        if (enhancerSessionId != sessionId) {
            runCatching { enhancer?.release() }
            enhancer = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
            enhancerSessionId = sessionId
        }

        enhancer?.let { effect ->
            runCatching {
                effect.setTargetGain((amplifierDb * 100f).toInt())
                effect.enabled = amplifierDb > 0.01f
            }
        }
    }

    private fun persistPosition(id: Long, positionMs: Long) {
        scope.launch(Dispatchers.IO) {
            runCatching { dao.savePosition(id, positionMs) }
        }
    }

    private fun saveCurrentPosition() {
        if (!::player.isInitialized) return
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val pos = player.currentPosition.coerceAtLeast(0)
        lastPositionSnapshot = pos
        persistPosition(id, pos)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveCurrentPosition()
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        saveCurrentPosition()
        runCatching { enhancer?.release() }
        enhancer = null
        if (::session.isInitialized) session.release()
        if (::player.isInitialized) player.release()
        scope.cancel()
        super.onDestroy()
    }
}
