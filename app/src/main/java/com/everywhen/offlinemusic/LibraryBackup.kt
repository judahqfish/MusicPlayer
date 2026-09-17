package com.everywhen.offlinemusic

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Writes a small, human-readable backup of library organization data outside
 * the app's private storage so it survives an uninstall.
 *
 * The backup intentionally does not copy audio. It records playlist order and
 * tag membership against stable, human-readable track identity fields.
 */
suspend fun writeLibraryBackup(context: Context, repo: MusicRepository): Boolean = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false

    val playlists = repo.playlists.first()
    val tags = repo.tags.first()

    fun trackJson(track: TrackEntity): JSONObject = JSONObject().apply {
        put("fileName", track.fileName)
        put("displayName", track.displayName ?: JSONObject.NULL)
        put("artist", track.artist ?: JSONObject.NULL)
        put("album", track.album ?: JSONObject.NULL)
    }

    val playlistArray = JSONArray()
    playlists.forEach { playlist ->
        val orderedTracks = repo.observePlaylistTracks(playlist.id).first()
        playlistArray.put(JSONObject().apply {
            put("name", playlist.name)
            put("tracks", JSONArray().apply { orderedTracks.forEach { put(trackJson(it)) } })
        })
    }

    val tagArray = JSONArray()
    tags.forEach { tag ->
        val taggedTracks = repo.observeTagTracks(tag.id).first()
        tagArray.put(JSONObject().apply {
            put("name", tag.name)
            put("tracks", JSONArray().apply { taggedTracks.forEach { put(trackJson(it)) } })
        })
    }

    val root = JSONObject().apply {
        put("format", "MusicPlayer library backup")
        put("version", 1)
        put("createdAt", System.currentTimeMillis())
        put("playlists", playlistArray)
        put("tags", tagArray)
    }

    val resolver = context.contentResolver
    val relativePath = "Download/MusicPlayer Backups/"
    val displayName = "MusicPlayer-library-backup-latest.json"
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

    val existing = resolver.query(
        collection,
        arrayOf(MediaStore.MediaColumns._ID),
        "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
        arrayOf(displayName, relativePath),
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            android.content.ContentUris.withAppendedId(collection, cursor.getLong(0))
        } else null
    }

    val uri = existing ?: resolver.insert(
        collection,
        ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
    ) ?: return@withContext false

    return@withContext runCatching {
        resolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8).use { writer ->
            requireNotNull(writer) { "Could not open backup file" }
            writer.write(root.toString(2))
        }
        true
    }.getOrDefault(false)
}
