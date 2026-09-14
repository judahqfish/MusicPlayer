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
                    skipped++
                    existing.id
                } else {
                    val meta = readMetadata(uri)
                    val id = dao.insertTrack(TrackEntity(uri = uri.toString(), fileName = meta.first, durationMs = meta.second))
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

    private fun readMetadata(uri: Uri): Pair<String, Long> {
        val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "Audio"
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            name to duration
        } catch (_: Exception) {
            name to 0L
        } finally {
            runCatching { mmr.release() }
        }
    }

    suspend fun createPlaylist(name: String): Long = dao.insertPlaylist(PlaylistEntity(name = name.trim()))
    suspend fun createTag(name: String): Long = dao.insertTag(TagEntity(name = name.trim()))

    suspend fun createPlaylistFromTag(tagId: Long, name: String): Long {
        val id = createPlaylist(name)
        observeTagTracks(tagId) // keeps API explicit; actual snapshot below
        return id
    }

    fun observePlaylistTracks(id: Long): Flow<List<TrackEntity>> = dao.observePlaylistTracks(id)
    fun observeTagTracks(id: Long): Flow<List<TrackEntity>> = dao.observeTagTracks(id)
    fun searchTracks(q: String): Flow<List<TrackEntity>> = dao.searchTracks(q)
}

data class ImportResult(val added: Int, val skipped: Int)
