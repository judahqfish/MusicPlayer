package com.everywhen.offlinemusic

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class MusicRepository(private val context: Context, val dao: MusicDao) {
    val tracks = dao.observeTracks()
    val favorites = dao.observeFavorites()
    val playlists = dao.observePlaylists()
    val tags = dao.observeTags()

    private val importedDir: File
        get() = File(context.filesDir, "imported_audio").apply { mkdirs() }

    suspend fun importUris(uris: List<Uri>, playlistId: Long? = null): ImportResult = withContext(Dispatchers.IO) {
        var added = 0
        var skipped = 0
        var failed = 0
        uris.forEach { sourceUri ->
            runCatching {
                takePermission(sourceUri)
                val displayName = sourceDisplayName(sourceUri)
                val localUri = ensureLocalCopy(sourceUri, displayName)
                val existing = dao.trackByUri(localUri.toString())
                val trackId = if (existing != null) {
                    if (!existing.metadataScanned) {
                        val meta = readMetadata(localUri, displayName)
                        dao.updateMetadata(existing.id, meta.artist, meta.album, meta.durationMs)
                    }
                    skipped++
                    existing.id
                } else {
                    val meta = readMetadata(localUri, displayName)
                    val id = dao.insertTrack(
                        TrackEntity(
                            uri = localUri.toString(),
                            fileName = meta.fileName,
                            durationMs = meta.durationMs,
                            artist = meta.artist,
                            album = meta.album,
                            metadataScanned = true
                        )
                    )
                    if (id > 0) added++ else skipped++
                    id
                }
                if (playlistId != null && trackId > 0) {
                    dao.addToPlaylist(PlaylistTrackCrossRef(playlistId, trackId, dao.nextPlaylistOrder(playlistId)))
                }
            }.onFailure {
                failed++
            }
        }
        ImportResult(added, skipped, failed)
    }

    suspend fun importFolder(treeUri: Uri, playlistId: Long? = null): ImportResult = withContext(Dispatchers.IO) {
        takePermission(treeUri)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext ImportResult(0, 0, 1)
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

    /**
     * Existing SAF/cloud tracks from older app versions are converted to app-private
     * files when they are still readable. IDs stay the same, so playlists, tags,
     * Favorites, manual order and saved position are preserved.
     */
    suspend fun migrateExistingTracksToOfflineCopies(): Int = withContext(Dispatchers.IO) {
        var migrated = 0
        dao.allTracks().forEach { track ->
            val current = Uri.parse(track.uri)
            if (current.scheme == "content") {
                runCatching {
                    val local = ensureLocalCopy(current, track.fileName)
                    dao.updateTrackUri(track.id, local.toString())
                    migrated++
                }
            }
        }
        migrated
    }

    suspend fun removeTracksFromLibrary(trackIds: Set<Long>) = withContext(Dispatchers.IO) {
        trackIds.forEach { id ->
            dao.track(id)?.let { track ->
                deleteManagedCopyIfPresent(Uri.parse(track.uri))
                dao.deleteTrack(track)
            }
        }
    }

    suspend fun refreshMissingMetadata() = withContext(Dispatchers.IO) {
        dao.tracksNeedingMetadata().forEach { track ->
            runCatching {
                val meta = readMetadata(Uri.parse(track.uri), track.fileName)
                dao.updateMetadata(track.id, meta.artist, meta.album, meta.durationMs)
            }.onFailure {
                runCatching { dao.updateMetadata(track.id, null, null, track.durationMs) }
            }
        }
    }

    private fun ensureLocalCopy(sourceUri: Uri, displayName: String): Uri {
        if (isManagedCopy(sourceUri)) return sourceUri
        if (sourceUri.scheme == "file") return sourceUri

        val extension = displayName.substringAfterLast('.', "")
            .takeIf { it.length in 1..8 && it.all { ch -> ch.isLetterOrDigit() } }
            ?.let { ".${it.lowercase()}" }
            ?: ""
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sourceUri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        val target = File(importedDir, "$digest$extension")

        if (!target.exists() || target.length() == 0L) {
            val temp = File(importedDir, "$digest.tmp")
            context.contentResolver.openInputStream(sourceUri).use { input ->
                requireNotNull(input) { "Could not open selected audio file" }
                temp.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            if (temp.length() == 0L) {
                temp.delete()
                error("Selected audio file was empty")
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
        return Uri.fromFile(target)
    }

    private fun deleteManagedCopyIfPresent(uri: Uri) {
        if (!isManagedCopy(uri)) return
        runCatching { File(requireNotNull(uri.path)).delete() }
    }

    private fun isManagedCopy(uri: Uri): Boolean {
        if (uri.scheme != "file") return false
        val path = uri.path ?: return false
        val file = File(path)
        return runCatching { file.canonicalPath.startsWith(importedDir.canonicalPath + File.separator) }.getOrDefault(false)
    }

    private fun sourceDisplayName(uri: Uri): String =
        DocumentFile.fromSingleUri(context, uri)?.name
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "Audio"

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

    private fun readMetadata(uri: Uri, fallbackName: String = "Audio"): LocalMetadata {
        val name = if (uri.scheme == "file") {
            fallbackName.ifBlank { File(uri.path.orEmpty()).name.ifBlank { "Audio" } }
        } else {
            DocumentFile.fromSingleUri(context, uri)?.name ?: fallbackName
        }
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

data class ImportResult(val added: Int, val skipped: Int, val failed: Int = 0)
private data class LocalMetadata(val fileName: String, val durationMs: Long, val artist: String?, val album: String?)
