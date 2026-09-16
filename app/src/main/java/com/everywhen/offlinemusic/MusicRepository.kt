package com.everywhen.offlinemusic

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
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
                val repairedUri = repairManagedCopyUri(localUri)
                val existing = dao.trackByUri(repairedUri.toString())
                val trackId = if (existing != null) {
                    val meta = readMetadata(repairedUri, displayName)
                    dao.updateMetadata(
                        existing.id,
                        meta.artist ?: existing.artist,
                        meta.album ?: existing.album,
                        meta.durationMs.takeIf { it > 0 } ?: existing.durationMs
                    )
                    skipped++
                    existing.id
                } else {
                    val meta = readMetadata(repairedUri, displayName)
                    val id = dao.insertTrack(
                        TrackEntity(
                            uri = repairedUri.toString(),
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

    suspend fun migrateExistingTracksToOfflineCopies(): Int = withContext(Dispatchers.IO) {
        var migrated = 0
        dao.allTracks().forEach { track ->
            val current = Uri.parse(track.uri)
            if (current.scheme == "content") {
                runCatching {
                    val local = ensureLocalCopy(current, track.fileName)
                    val repaired = repairManagedCopyUri(local)
                    val meta = readMetadata(repaired, track.fileName)
                    dao.updateTrackUri(track.id, repaired.toString())
                    dao.updateMetadata(
                        track.id,
                        meta.artist ?: track.artist,
                        meta.album ?: track.album,
                        meta.durationMs.takeIf { it > 0 } ?: track.durationMs
                    )
                    migrated++
                }
            } else if (isManagedCopy(current)) {
                runCatching { repairTrack(track) }
            }
        }
        migrated
    }

    suspend fun prepareTracksForPlayback(input: List<TrackEntity>): PlaybackPreparation = withContext(Dispatchers.IO) {
        val prepared = mutableListOf<TrackEntity>()
        val failed = mutableSetOf<Long>()
        input.forEach { supplied ->
            val latest = dao.track(supplied.id) ?: supplied
            val result = runCatching {
                val current = Uri.parse(latest.uri)
                val updated = when {
                    current.scheme == "content" -> {
                        val local = ensureLocalCopy(current, latest.fileName)
                        val repaired = repairManagedCopyUri(local)
                        val meta = readMetadata(repaired, latest.fileName)
                        dao.updateTrackUri(latest.id, repaired.toString())
                        dao.updateMetadata(
                            latest.id,
                            meta.artist ?: latest.artist,
                            meta.album ?: latest.album,
                            meta.durationMs.takeIf { it > 0 } ?: latest.durationMs
                        )
                        latest.copy(
                            uri = repaired.toString(),
                            durationMs = meta.durationMs.takeIf { it > 0 } ?: latest.durationMs,
                            artist = meta.artist ?: latest.artist,
                            album = meta.album ?: latest.album,
                            metadataScanned = true,
                            unavailable = false
                        )
                    }
                    current.scheme == "file" -> repairTrack(latest)
                    else -> latest
                }
                val uri = Uri.parse(updated.uri)
                if (uri.scheme == "file") {
                    val f = File(uri.path ?: error("Missing local file path"))
                    require(f.exists() && f.length() > 0L) { "Offline copy is missing" }
                }
                updated
            }
            result.onSuccess { prepared += it }.onFailure { failed += supplied.id }
        }
        PlaybackPreparation(prepared, failed)
    }

    private suspend fun repairTrack(track: TrackEntity): TrackEntity {
        var uri = Uri.parse(track.uri)
        if (isManagedCopy(uri)) uri = repairManagedCopyUri(uri)

        val meta = readMetadata(uri, track.fileName)
        val duration = meta.durationMs.takeIf { it > 0 } ?: track.durationMs
        val artist = meta.artist ?: track.artist
        val album = meta.album ?: track.album

        if (uri.toString() != track.uri) dao.updateTrackUri(track.id, uri.toString())
        if (duration != track.durationMs || artist != track.artist || album != track.album || !track.metadataScanned) {
            dao.updateMetadata(track.id, artist, album, duration)
        }
        return track.copy(
            uri = uri.toString(),
            durationMs = duration,
            artist = artist,
            album = album,
            metadataScanned = true,
            unavailable = false
        )
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
                if (isManagedCopy(Uri.parse(track.uri))) {
                    repairTrack(track)
                } else {
                    val meta = readMetadata(Uri.parse(track.uri), track.fileName)
                    dao.updateMetadata(track.id, meta.artist ?: track.artist, meta.album ?: track.album, meta.durationMs.takeIf { it > 0 } ?: track.durationMs)
                }
            }.onFailure {
                runCatching { dao.updateMetadata(track.id, track.artist, track.album, track.durationMs) }
            }
        }
    }

    private fun ensureLocalCopy(sourceUri: Uri, displayName: String): Uri {
        if (isManagedCopy(sourceUri)) return sourceUri
        if (sourceUri.scheme == "file") return sourceUri

        val extension = preferredExtension(sourceUri, displayName)
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

    private fun preferredExtension(uri: Uri, displayName: String): String {
        val fromName = displayName.substringAfterLast('.', "").lowercase()
        if (fromName in audioExtensions) return ".$fromName"
        val mime = runCatching { context.contentResolver.getType(uri)?.lowercase() }.getOrNull()
        return when (mime) {
            "audio/mpeg", "audio/mp3" -> ".mp3"
            "audio/mp4", "audio/x-m4a", "audio/m4a" -> ".m4a"
            "audio/aac", "audio/aacp" -> ".aac"
            "audio/flac", "audio/x-flac" -> ".flac"
            "audio/ogg", "application/ogg" -> ".ogg"
            "audio/opus" -> ".opus"
            "audio/wav", "audio/x-wav", "audio/wave" -> ".wav"
            else -> ""
        }
    }

    private fun repairManagedCopyUri(uri: Uri): Uri {
        if (!isManagedCopy(uri)) return uri
        val file = File(uri.path ?: return uri)
        if (!file.exists() || file.length() == 0L) return uri
        val ext = file.extension.lowercase()
        if (ext in audioExtensions) return uri
        val inferred = sniffAudioExtension(file) ?: return uri
        val renamed = File(file.parentFile, "${file.nameWithoutExtension}.$inferred")
        if (renamed.exists() && renamed.length() > 0L) {
            if (file.absolutePath != renamed.absolutePath) file.delete()
            return Uri.fromFile(renamed)
        }
        if (file.renameTo(renamed)) return Uri.fromFile(renamed)
        return runCatching {
            file.copyTo(renamed, overwrite = true)
            file.delete()
            Uri.fromFile(renamed)
        }.getOrDefault(uri)
    }

    private fun sniffAudioExtension(file: File): String? = runCatching {
        val header = ByteArray(16)
        val count = FileInputStream(file).use { it.read(header) }
        if (count < 4) return@runCatching null
        val ascii = header.copyOf(count).toString(Charsets.ISO_8859_1)
        when {
            ascii.startsWith("ID3") -> "mp3"
            header[0].toInt() and 0xFF == 0xFF && (header[1].toInt() and 0xE0) == 0xE0 -> {
                val layerBits = header[1].toInt() and 0x06
                if (layerBits != 0) "mp3" else "aac"
            }
            ascii.startsWith("fLaC") -> "flac"
            ascii.startsWith("OggS") -> "ogg"
            ascii.startsWith("RIFF") && ascii.length >= 12 && ascii.substring(8, 12) == "WAVE" -> "wav"
            ascii.length >= 8 && ascii.substring(4, 8) == "ftyp" -> "m4a"
            else -> null
        }
    }.getOrNull()

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

    private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")

    private fun isAudioName(name: String?): Boolean {
        val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return false
        return ext in audioExtensions
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
            if (uri.scheme == "file" && !uri.path.isNullOrBlank()) mmr.setDataSource(uri.path) else mmr.setDataSource(context, uri)
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
data class PlaybackPreparation(val tracks: List<TrackEntity>, val failedTrackIds: Set<Long>)
private data class LocalMetadata(val fileName: String, val durationMs: Long, val artist: String?, val album: String?)
