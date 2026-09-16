package com.everywhen.offlinemusic

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
    val amplifierDb = prefs.amplifierDb.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var connecting = false

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        connectController()
        viewModelScope.launch {
            val migrated = repo.migrateExistingTracksToOfflineCopies()
            repo.refreshMissingMetadata()
            if (migrated > 0) {
                _message.value = "$migrated existing track${if (migrated == 1) "" else "s"} saved for offline playback"
            }
        }
        viewModelScope.launch {
            speed.drop(1).collect { _controller.value?.setPlaybackSpeed(it) }
        }
    }

    private fun connectController() {
        if (connecting || _controller.value != null) return
        connecting = true
        viewModelScope.launch {
            try {
                repeat(3) { attempt ->
                    try {
                        val token = SessionToken(application, ComponentName(application, PlaybackService::class.java))
                        controllerFuture = MediaController.Builder(application, token).buildAsync()
                        val connected = controllerFuture!!.await()
                        connected.setPlaybackSpeed(speed.value)
                        _controller.value = connected
                        return@launch
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        controllerFuture?.let { runCatching { MediaController.releaseFuture(it) } }
                        controllerFuture = null
                        if (attempt < 2) delay(600L * (attempt + 1))
                    }
                }
                _message.value = "Playback service could not start. Please reopen the app."
            } finally {
                connecting = false
            }
        }
    }

    override fun onCleared() {
        controllerFuture?.let { runCatching { MediaController.releaseFuture(it) } }
        super.onCleared()
    }

    private fun importMessage(r: ImportResult): String = buildString {
        append("${r.added} added for offline playback")
        if (r.skipped > 0) append(" · ${r.skipped} already present")
        if (r.failed > 0) append(" · ${r.failed} could not be copied")
    }

    fun importFiles(uris: List<Uri>, playlistId: Long? = null) = viewModelScope.launch {
        _message.value = importMessage(repo.importUris(uris, playlistId))
    }

    fun importFolder(uri: Uri, playlistId: Long? = null) = viewModelScope.launch {
        _message.value = importMessage(repo.importFolder(uri, playlistId))
    }

    fun clearMessage() { _message.value = null }

    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        if (name.isNotBlank()) onCreated(repo.createPlaylist(name))
    }

    fun createTag(name: String, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        if (name.isNotBlank()) onCreated(repo.createTag(name))
    }

    fun createTagsFromText(raw: String, trackIds: Set<Long> = emptySet()) = viewModelScope.launch {
        val names = raw
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        if (names.isEmpty()) return@launch

        var applied = 0
        names.forEach { name ->
            val existing = tags.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val tagId = existing?.id ?: repo.createTag(name).takeIf { it > 0 }
            if (tagId != null) {
                trackIds.forEach { trackId -> repo.dao.addTrackTag(TrackTagCrossRef(trackId, tagId)) }
                applied++
            }
        }
        _message.value = if (trackIds.isEmpty()) {
            "$applied tags created or already available"
        } else {
            "$applied tags applied to ${trackIds.size} tracks"
        }
    }

    fun toggleFavorite(track: TrackEntity) = viewModelScope.launch { repo.dao.setFavorite(track.id, !track.favorite) }
    fun renameTrack(track: TrackEntity, name: String) = viewModelScope.launch { repo.dao.renameTrack(track.id, name.trim().ifBlank { null }) }

    fun setFavoriteForTracks(trackIds: Set<Long>, favorite: Boolean) = viewModelScope.launch {
        trackIds.forEach { repo.dao.setFavorite(it, favorite) }
        _message.value = if (favorite) "${trackIds.size} tracks added to Favorites" else "${trackIds.size} tracks removed from Favorites"
    }

    fun removeTracksFromLibrary(trackIds: Set<Long>) = viewModelScope.launch {
        if (trackIds.isEmpty()) return@launch
        _controller.value?.let { c ->
            for (index in c.mediaItemCount - 1 downTo 0) {
                if (c.getMediaItemAt(index).mediaId.toLongOrNull() in trackIds) {
                    runCatching { c.removeMediaItem(index) }
                }
            }
        }
        repo.removeTracksFromLibrary(trackIds)
        _message.value = "${trackIds.size} track${if (trackIds.size == 1) "" else "s"} removed from MusicPlayer · original source files kept"
    }

    fun addTrackToPlaylist(trackId: Long, playlistId: Long) = viewModelScope.launch {
        repo.dao.addToPlaylist(PlaylistTrackCrossRef(playlistId, trackId, repo.dao.nextPlaylistOrder(playlistId)))
    }

    fun addTracksToPlaylist(trackIds: Set<Long>, playlistId: Long) = viewModelScope.launch {
        var order = repo.dao.nextPlaylistOrder(playlistId)
        trackIds.forEach { trackId ->
            repo.dao.addToPlaylist(PlaylistTrackCrossRef(playlistId, trackId, order++))
        }
        _message.value = "${trackIds.size} tracks added to playlist"
    }

    fun savePlaylistOrder(playlistId: Long, orderedTrackIds: List<Long>) = viewModelScope.launch {
        orderedTrackIds.forEachIndexed { index, trackId ->
            repo.dao.setPlaylistOrder(playlistId, trackId, index)
        }
    }

    fun removeFromPlaylist(trackId: Long, playlistId: Long) = viewModelScope.launch {
        repo.dao.removeFromPlaylist(playlistId, trackId)
    }

    fun setTag(trackId: Long, tagId: Long, selected: Boolean) = viewModelScope.launch {
        if (selected) repo.dao.addTrackTag(TrackTagCrossRef(trackId, tagId)) else repo.dao.removeTrackTag(trackId, tagId)
    }

    fun addTagsToTracks(trackIds: Set<Long>, tagIds: Set<Long>) = viewModelScope.launch {
        trackIds.forEach { trackId ->
            tagIds.forEach { tagId -> repo.dao.addTrackTag(TrackTagCrossRef(trackId, tagId)) }
        }
        _message.value = "Tags added to ${trackIds.size} tracks"
    }

    fun setSpeed(value: Float) = viewModelScope.launch { prefs.setSpeed(value) }
    fun setAmplifierDb(value: Float) = viewModelScope.launch { prefs.setAmplifierDb(value) }

    fun playTracks(queue: List<TrackEntity>, startTrackId: Long, shuffle: Boolean = false) {
        val c = _controller.value
        if (c == null) {
            connectController()
            _message.value = "Starting playback service… tap the track again in a moment."
            return
        }
        val available = queue.filterNot { it.unavailable }
        if (available.isEmpty()) {
            _message.value = "No available audio files in this queue."
            return
        }
        val start = available.indexOfFirst { it.id == startTrackId }.let { if (it >= 0) it else 0 }
        val items = available.map { track ->
            val metadata = MediaMetadata.Builder().setTitle(track.title())
            track.artist?.takeIf { it.isNotBlank() }?.let(metadata::setArtist)
            track.album?.takeIf { it.isNotBlank() }?.let(metadata::setAlbumTitle)
            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.uri)
                .setMediaMetadata(metadata.build())
                .build()
        }
        runCatching {
            c.setMediaItems(items, start, available.getOrNull(start)?.lastPositionMs ?: 0L)
            c.shuffleModeEnabled = shuffle
            c.prepare()
            c.play()
        }.onFailure {
            _message.value = "That file could not be played."
        }
    }

    fun playPause() {
        val c = _controller.value
        if (c == null) connectController() else if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { _controller.value?.seekToNextMediaItem() }

    fun previous() {
        _controller.value?.let {
            if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem()
        }
    }

    fun seekBack() {
        _controller.value?.let { c -> c.seekTo((c.currentPosition - 10_000L).coerceAtLeast(0L)) }
    }

    fun seekForward() {
        _controller.value?.let { c ->
            val duration = c.duration.takeIf { it > 0 }
            val target = c.currentPosition + 10_000L
            c.seekTo(if (duration != null) target.coerceAtMost(duration) else target)
        }
    }

    fun setRepeatMode(mode: Int) { _controller.value?.repeatMode = mode }
    fun toggleShuffle() { _controller.value?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
}
