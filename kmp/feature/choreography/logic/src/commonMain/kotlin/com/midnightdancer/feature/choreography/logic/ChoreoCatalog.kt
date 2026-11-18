package com.midnightdancer.feature.choreography.logic

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.media.ElementDurationCodec
import com.midnightdancer.core.domain.styles.DanceStyleCatalogSnapshot

/** One pickable catalog row (element or track) scoped to a dance style. */
data class ChoreoCatalogEntry(
    val id: String,
    val title: String,
    val durationMs: Long,
    val style: String,
    val subtitle: String = "",
    /** Optional content URI for future timeline preview. */
    val mediaUri: String? = null,
)

/**
 * Style-scoped catalog for Pro Editor pickers (C-185).
 * Prefer live Room/[AppData] rows; empty until [replaceFromAppData] runs.
 */
object ChoreoCatalog {
    @Volatile private var canonical: DanceStyleCatalogSnapshot? = null
    fun replaceStyleCatalog(snapshot: DanceStyleCatalogSnapshot?) { canonical = snapshot }
    fun displayStyle(value: String): String = canonical?.displayName(value) ?: value
    fun canonicalStyle(value: String): String = canonical?.canonicalId(value) ?: liveStyleIds[value] ?: value
    @Volatile private var liveStyleIds: Map<String, String> = emptyMap()
    private val defaultStyles: List<String> =
        listOf("Sensual", "Bachata", "Kizomba", "Dominican", "Modern", "Salsa")

    @Volatile
    private var liveStyles: List<String> = emptyList()

    @Volatile
    private var liveElements: List<ChoreoCatalogEntry> = emptyList()

    @Volatile
    private var liveTracks: List<ChoreoCatalogEntry> = emptyList()

    val styles: List<String>
        get() = canonical?.entries?.map { it.name } ?: liveStyles.ifEmpty { defaultStyles }

    fun replaceFromAppData(data: AppData) {
        liveStyleIds = data.danceStyles.flatMap { listOf(it.id to it.id, it.name to it.id) }.toMap()
        liveStyles = data.danceStyles.filterNot { it.id == "__md_choreo_scoped__" }.map { it.name }.filter { it.isNotBlank() }.distinct()
        liveElements = data.danceStyles.filterNot { it.id == "__md_choreo_scoped__" }.flatMap { style ->
            style.moves.map { move ->
                val dur = ElementDurationCodec.durationMs(move.description).takeIf { it > 0L }
                    ?: 30_000L
                ChoreoCatalogEntry(
                    id = move.id,
                    title = move.name,
                    durationMs = dur,
                    style = style.id,
                    subtitle = move.level,
                    mediaUri = move.videoUri,
                )
            }
        }
        liveTracks = data.songs.map { song ->
            val sec = song.duration.toInt().coerceAtLeast(0)
            ChoreoCatalogEntry(
                id = song.id,
                title = song.title,
                durationMs = (song.duration * 1000.0).toLong().coerceAtLeast(1_000L),
                style = song.danceStyle,
                subtitle = if (sec > 0) EditorTrackDurations.formatMmSs(sec) else song.level,
                mediaUri = song.fileName.takeIf { it.isNotBlank() },
            )
        }
    }

    fun elementsForStyle(style: String): List<ChoreoCatalogEntry> {
        if (style.isBlank()) return emptyList()
        val needle = canonicalStyle(style)
        val matched = liveElements.filter {
            needle.isNotBlank() && canonicalStyle(it.style) == needle
        }
        // Unknown/empty styles must not expose media from another style.
        val scoped = matched
        // C-275: choreo Add picker — only elements with video (blank mediaUri cannot be added).
        return scoped.filter { !it.mediaUri.isNullOrBlank() }
    }

    fun tracksForStyle(style: String): List<ChoreoCatalogEntry> =
        liveTracks.filter {
            style.isNotBlank() && canonicalStyle(it.style) == canonicalStyle(style)
        }

    /** Snapshot of all live element rows (for UI refresh keys). */
    fun elementCount(): Int = liveElements.size

    fun elementClip(entry: ChoreoCatalogEntry) =
        EditorClip(entry.title, entry.durationMs, entry.mediaUri)

    fun trackClip(entry: ChoreoCatalogEntry) =
        EditorClip(entry.title, entry.durationMs, entry.mediaUri)

    fun elementDurationMs(label: String): Long =
        liveElements.firstOrNull { it.title == label }?.durationMs
            ?: EditorTrackDurations.elementPadMs()

    fun trackDurationMs(label: String): Long =
        liveTracks.firstOrNull { it.title == label }?.durationMs
            ?: (EditorTrackDurations.secondsFor(label) * 1000L)
}

/** «копия - X», «копия1 - X», «копия2 - X» … (C-135 · 3.5). */
object ChoreographyCopyNaming {
    private val prefix = Regex("^копия\\d* - ")

    fun baseName(title: String): String = title.replace(prefix, "")

    fun nextCopyTitle(sourceTitle: String, existingTitles: Collection<String>): String {
        val base = baseName(sourceTitle)
        val taken = existingTitles.map { it.trim().lowercase() }.toSet()
        var n = 0
        while (true) {
            val candidate = if (n == 0) "копия - $base" else "копия$n - $base"
            if (candidate.lowercase() !in taken) return candidate
            n++
        }
    }
}
