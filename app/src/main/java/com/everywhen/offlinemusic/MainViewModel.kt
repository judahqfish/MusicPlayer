package com.everywhen.offlinemusic

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as MusicApplication
    val repo = application.repository
    private val prefs = PlayerPreferences(app)

    val tracks = repo.tracks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists = repo.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tags = repo.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorites = repo.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val speed = prefs.speed.stateIn(viewModelScope, SharingStarted.Eagerly, 1f)

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        viewModelScope.launch {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(app, token).buildAsync()
            _controller.value = controllerFuture!!.await()
            _controller.value?.setPlaybackSpeed(speed.value)
        }
        viewModelScope.launch {
            speed.drop(1).collect { _controller.value?.setPlaybackSpeed(it) }
        }
    }

    override fun onCleared() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }

    fun importFiles(uris: List<Uri>, playlistId: Long? = null) = viewModelScope.launch {
        val r = repo.importUris(uris, playlistId)
        _message.value = "${r.added} added${if (r.skipped > 0) " · ${r.skipped} already present" else ""}"
    }

    fun importFolder(uri: Uri, playlistId: Long? = null) = viewModelScope.launch {
        val r = repo.importFolder(uri, playlistId)
        _message.value = "${r.added} added${if (r.skipped > 0) " · ${r.skipped} already present" else ""}"
    }

    fun clearMessage() { _message.value = null }

    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        if (name.isNotBlank()) onCreated(repo.createPlaylist(name))
    }

    fun createTag(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) repo.createTag(name)
    }

    fun toggleFavorite(track: TrackEntity) = viewModelScope.launch { repo.dao.setFavorite(track.id, !track.favorite) }
    fun renameTrack(track: TrackEntity, name: String) = viewModelScope.launch { repo.dao.renameTrack(track.id, name.trim().ifBlank { null }) }

    fun addTrackToPlaylist(trackId: Long, playlistId: Long) = viewModelScope.launch {
        repo.dao.addToPlaylist(PlaylistTrackCrossRef(playlistId, trackId, repo.dao.nextPlaylistOrder(playlistId)))
    }

    fun removeFromPlaylist(trackId: Long, playlistId: Long) = viewModelScope.launch { repo.dao.removeFromPlaylist(playlistId, trackId) }

    fun setTag(trackId: Long, tagId: Long, selected: Boolean) = viewModelScope.launch {
        if (selected) repo.dao.addTrackTag(TrackTagCrossRef(trackId, tagId)) else repo.dao.removeTrackTag(trackId, tagId)
    }

    fun setSpeed(value: Float) = viewModelScope.launch { prefs.setSpeed(value) }

    fun playTracks(queue: List<TrackEntity>, startTrackId: Long, shuffle: Boolean = false) {
        val c = _controller.value ?: return
        val start = queue.indexOfFirst { it.id == startTrackId }.coerceAtLeast(0)
        val items = queue.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title()).build())
                .build()
        }
        c.setMediaItems(items, start, queue.getOrNull(start)?.lastPositionMs ?: 0L)
        c.shuffleModeEnabled = shuffle
        c.prepare()
        c.play()
    }

    fun playPause() {
        _controller.value?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() { _controller.value?.seekToNextMediaItem() }
    fun previous() {
        _controller.value?.let {
            if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem()
        }
    }

    fun setRepeatMode(mode: Int) { _controller.value?.repeatMode = mode }
    fun toggleShuffle() { _controller.value?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
}
