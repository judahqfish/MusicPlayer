package com.everywhen.offlinemusic

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks", indices = [Index(value = ["uri"], unique = true)])
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val fileName: String,
    val displayName: String? = null,
    val durationMs: Long = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val lastPositionMs: Long = 0,
    val unavailable: Boolean = false,
    val artist: String? = null,
    val album: String? = null,
    val metadataScanned: Boolean = false
) {
    private fun baseTitle(): String = displayName?.takeIf { it.isNotBlank() }
        ?: fileName.substringBeforeLast('.', fileName)

    fun title(): String {
        val base = baseTitle()
        val cleanAlbum = album?.takeIf { it.isNotBlank() }
        val cleanArtist = artist?.takeIf { it.isNotBlank() }
        return when {
            cleanAlbum != null && cleanArtist != null -> "$cleanAlbum · $base — $cleanArtist"
            cleanAlbum != null -> "$cleanAlbum · $base"
            cleanArtist != null -> "$base — $cleanArtist"
            else -> base
        }
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTrackId: Long? = null,
    val shuffleEnabled: Boolean = false
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("trackId")]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val sortOrder: Int
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "track_tags",
    primaryKeys = ["trackId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tagId")]
)
data class TrackTagCrossRef(val trackId: Long, val tagId: Long)

data class PlaylistSummary(val id: Long, val name: String, val count: Int)
data class TagSummary(val id: Long, val name: String, val count: Int)

@Dao
interface MusicDao {
    @Query("SELECT * FROM tracks ORDER BY fileName COLLATE NOCASE")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY fileName COLLATE NOCASE")
    suspend fun allTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE favorite = 1 ORDER BY fileName COLLATE NOCASE")
    fun observeFavorites(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun track(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE uri = :uri LIMIT 1")
    suspend fun trackByUri(uri: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE metadataScanned = 0")
    suspend fun tracksNeedingMetadata(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Update suspend fun updateTrack(track: TrackEntity)

    @Query("UPDATE tracks SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE tracks SET displayName = :name WHERE id = :id")
    suspend fun renameTrack(id: Long, name: String?)

    @Query("UPDATE tracks SET artist = :artist, album = :album, durationMs = :duration, metadataScanned = 1 WHERE id = :id")
    suspend fun updateMetadata(id: Long, artist: String?, album: String?, duration: Long)

    @Query("UPDATE tracks SET durationMs = :duration WHERE id = :id")
    suspend fun updateDuration(id: Long, duration: Long)

    @Query("UPDATE tracks SET uri = :uri, unavailable = 0 WHERE id = :id")
    suspend fun updateTrackUri(id: Long, uri: String)

    @Query("UPDATE tracks SET lastPositionMs = :position WHERE id = :id")
    suspend fun savePosition(id: Long, position: Long)

    @Query("UPDATE tracks SET uri = :uri, fileName = :fileName, durationMs = :duration, unavailable = 0, metadataScanned = 0 WHERE id = :id")
    suspend fun relinkTrack(id: Long, uri: String, fileName: String, duration: Long)

    @Delete suspend fun deleteTrack(track: TrackEntity)

    @Query("SELECT playlists.id, playlists.name, COUNT(playlist_tracks.trackId) AS count FROM playlists LEFT JOIN playlist_tracks ON playlists.id = playlist_tracks.playlistId GROUP BY playlists.id ORDER BY playlists.name COLLATE NOCASE")
    fun observePlaylists(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun playlist(id: Long): PlaylistEntity?

    @Insert suspend fun insertPlaylist(playlist: PlaylistEntity): Long
    @Update suspend fun updatePlaylist(playlist: PlaylistEntity)
    @Delete suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("SELECT tracks.* FROM tracks INNER JOIN playlist_tracks ON tracks.id = playlist_tracks.trackId WHERE playlist_tracks.playlistId = :playlistId ORDER BY playlist_tracks.sortOrder")
    fun observePlaylistTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextPlaylistOrder(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToPlaylist(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeFromPlaylist(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("UPDATE playlist_tracks SET sortOrder = :order WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun setPlaylistOrder(playlistId: Long, trackId: Long, order: Int)

    @Query("SELECT tags.id, tags.name, COUNT(track_tags.trackId) AS count FROM tags LEFT JOIN track_tags ON tags.id = track_tags.tagId GROUP BY tags.id ORDER BY tags.name COLLATE NOCASE")
    fun observeTags(): Flow<List<TagSummary>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE id = :id LIMIT 1")
    suspend fun tag(id: Long): TagEntity?

    @Update suspend fun updateTag(tag: TagEntity)
    @Delete suspend fun deleteTag(tag: TagEntity)

    @Query("SELECT tracks.* FROM tracks INNER JOIN track_tags ON tracks.id = track_tags.trackId WHERE track_tags.tagId = :tagId ORDER BY tracks.fileName COLLATE NOCASE")
    fun observeTagTracks(tagId: Long): Flow<List<TrackEntity>>

    @Query("SELECT tags.* FROM tags INNER JOIN track_tags ON tags.id = track_tags.tagId WHERE track_tags.trackId = :trackId ORDER BY tags.name COLLATE NOCASE")
    suspend fun tagsForTrack(trackId: Long): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrackTag(ref: TrackTagCrossRef)

    @Query("DELETE FROM track_tags WHERE trackId = :trackId AND tagId = :tagId")
    suspend fun removeTrackTag(trackId: Long, tagId: Long)

    @Query("SELECT * FROM tracks WHERE fileName LIKE '%' || :q || '%' OR displayName LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%' ORDER BY fileName COLLATE NOCASE")
    fun searchTracks(q: String): Flow<List<TrackEntity>>
}

@Database(
    entities = [TrackEntity::class, PlaylistEntity::class, PlaylistTrackCrossRef::class, TagEntity::class, TrackTagCrossRef::class],
    version = 2,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN artist TEXT")
                db.execSQL("ALTER TABLE tracks ADD COLUMN album TEXT")
                db.execSQL("ALTER TABLE tracks ADD COLUMN metadataScanned INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): MusicDatabase = Room.databaseBuilder(
            context.applicationContext,
            MusicDatabase::class.java,
            "offline_music.db"
        ).addMigrations(MIGRATION_1_2).build()
    }
}
