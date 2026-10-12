package com.midnightdancer.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Normalized dance style — moves live in [MoveEntity] (Style 1—N Move). */
@Entity(tableName = "dance_styles")
data class DanceStyleEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "current_move_id") val currentMoveId: String? = null,
)

@Entity(
    tableName = "moves",
    foreignKeys = [
        ForeignKey(
            entity = DanceStyleEntity::class,
            parentColumns = ["id"],
            childColumns = ["style_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["style_id"])],
)
data class MoveEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "style_id") val styleId: String,
    val name: String,
    val level: String,
    val description: String? = null,
    @ColumnInfo(name = "video_uri") val videoUri: String? = null,
    @ColumnInfo(name = "mastery_percent") val masteryPercent: Int = 0,
    /** Preserve nested list order from Flutter AppData. */
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "dance_style") val danceStyle: String,
    val level: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    val duration: Double,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Int,
    @ColumnInfo(name = "playback_speed") val playbackSpeed: Double = 1.0,
)

/**
 * Timeline stored as Flutter-compatible JSON object (`Map<double,String>` with string keys).
 */
@Entity(tableName = "choreographies")
data class ChoreographyEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "style_id") val styleId: String,
    @ColumnInfo(name = "timeline_json") val timelineJson: String = "{}",
    @ColumnInfo(name = "start_time") val startTime: Double = 0.0,
    @ColumnInfo(name = "end_time") val endTime: Double,
)

/**
 * Catalog meta blobs (settings JSON for AppData export parity).
 * Hot prefs stay in DataStore (C-11) — this is backup/export only, not a prefs mirror.
 */
@Entity(tableName = "catalog_meta")
data class CatalogMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

object CatalogMetaKeys {
    const val SETTINGS_JSON = "settings_json"
    const val CATALOG_MIGRATION_DONE = "catalog_migration_done"
}
