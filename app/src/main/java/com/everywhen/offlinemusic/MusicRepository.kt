package com.everywhen.offlinemusic

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context, val dao: MusicDao) {
    val tracks = dao.observeTracks()
    val favorites = dao.observeFavorites()
    val playlists = dao.observePlaylists()
    val tags = dao.observeTags()

    suspend fun importUris(uris: List<Uri>, playlistId: Long? = null): ImportResult = withContext(Dispatchers.IO) {
        var added = 0
        var skipped = 0
        uris.forEach { uri ->
            runCatching {
                takePermission(uri)
                val existing = dao.trackByUri(uri.toString())
                val trackId = if (existing != null) {
                    if (!existing.metadataScanned) {
                        val meta = readMetadata(uri)
                        dao.updateMetadata(existing.id, meta.artist, meta.album, meta.durationMs)
                    }
                    skipped++
                    existing.id
                } else {
                    val meta = readMetadata(uri)
                    val id = dao.insertTrack(
                        TrackEntity(
                            uri = uri.toString(),
                            fileName = meta.fileName,
                            durationMs = meta.durationMs,
                            artist = meta.artist,
                            album = meta.album,
                            metadataScanned = true
                        )
                    )
                    if (id > 0) added++
                    id
                }
                if (playlistId != null && trackId > 0) {
                    dao.addToPlaylist(PlaylistTrackCrossRef(playlistId, trackId, dao.nextPlaylistOrder(playlistId)))
                }
            }
        }
        ImportResult(added, skipped)
    }

    suspend fun importFolder(treeUri: Uri, playlistId: Long? = null): ImportResult = withContext(Dispatchers.IO) {
        takePermission(treeUri)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext ImportResult(0, 0)
        val audio = mutableListOf<Uri>()
        fun walk(dir: DocumentFile) {
            dir.listFiles().forEach { f ->
                if (f.isDirectory) walk(f)
                else if (f.type?.startsWith("audio/") == true || isAudioName(f.name)) audio += f.uri
            }
        }
        walk(root)
        importUris(audio, playlistId)
    }

    suspend fun refreshMissingMetadata() = withContext(Dispatchers.IO) {
        dao.tracksNeedingMetadata().forEach { track ->
            runCatching {
                val meta = readMetadata(Uri.parse(track.uri))
                dao.updateMetadata(track.id, meta.artist, meta.album, meta.durationMs)
            }.onFailure {
                // Mark as scanned so an unreadable/untagged file is not re-opened every launch.
                runCatching { dao.updateMetadata(track.id, null, null, track.durationMs) }
            }
        }
    }

    private fun isAudioName(name: String?): Boolean {
        val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return false
        return ext in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")
    }

    private fun takePermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun readMetadata(uri: Uri): LocalMetadata {
        val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "Audio"
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.trim()?.takeIf { it.isNotBlank() }
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.trim()?.takeIf { it.isNotBlank() }
            LocalMetadata(name, duration, artist, album)
        } catch (_: Exception) {
            LocalMetadata(name, 0L, null, null)
        } finally {
            runCatching { mmr.release() }
        }
    }

    suspend fun createPlaylist(name: String): Long = dao.insertPlaylist(PlaylistEntity(name = name.trim()))
    suspend fun createTag(name: String): Long = dao.insertTag(TagEntity(name = name.trim()))

    suspend fun createPlaylistFromTag(tagId: Long, name: String): Long {
        val id = createPlaylist(name)
        observeTagTracks(tagId)
        return id
    }

    fun observePlaylistTracks(id: Long): Flow<List<TrackEntity>> = dao.observePlaylistTracks(id)
    fun observeTagTracks(id: Long): Flow<List<TrackEntity>> = dao.observeTagTracks(id)
    fun searchTracks(q: String): Flow<List<TrackEntity>> = dao.searchTracks(q)
}

data class ImportResult(val added: Int, val skipped: Int)
private data class LocalMetadata(val fileName: String, val durationMs: Long, val artist: String?, val album: String?)
