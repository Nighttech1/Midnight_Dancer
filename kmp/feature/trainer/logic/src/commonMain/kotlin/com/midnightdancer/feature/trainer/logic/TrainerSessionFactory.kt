package com.midnightdancer.feature.trainer.logic

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.media.ElementDurationCodec
import com.midnightdancer.core.domain.settings.ElementCategoryConfig
import kotlin.random.Random

/** Builds a Live session from hub picks + Room [AppData] (C-192 / C-199 / C-225). */
object TrainerSessionFactory {
    const val TAG_ALL: String = "Все теги"

    fun isAllTags(tag: String): Boolean {
        val t = tag.trim()
        return t.isEmpty() ||
            t.equals(TAG_ALL, ignoreCase = true) ||
            t.equals("Все", ignoreCase = true) ||
            t.equals("Все уровни", ignoreCase = true)
    }

    fun build(hub: TrainerUiState, data: AppData?): TrainerSessionUiState {
        if (hub.mode == TrainerMode.Choreography) {
            return buildFromSelectedChoreography(hub, data)
        }
        val interval = hub.intervalSec.coerceIn(2f, 15f).let { if (it <= 0f) 8f else it }
        val style = data?.danceStyles?.firstOrNull {
            if (hub.selectedStyleId.isNotBlank()) it.id == hub.selectedStyleId
            else it.name.equals(hub.styleName, ignoreCase = true)
        }
        val tag = hub.elementTagLabel.trim()
        val tagIsAll = isAllTags(tag)
        val pool = style?.moves.orEmpty().filter { move ->
            tagIsAll || move.level.equals(tag, ignoreCase = true)
        }
        val song = data?.songs?.firstOrNull {
            it.title.equals(hub.trackTitle, ignoreCase = true)
        }
        val durationMs = ((song?.duration ?: 0.0) * 1000.0).toLong()
            .coerceAtLeast(60_000L)
        val sessionSec = (durationMs / 1000f).coerceAtLeast(interval)
        val cues = buildCueSchedule(
            pool = pool,
            interval = interval,
            sessionSec = sessionSec,
            fallbackLabel = hub.elementTagLabel,
            useElementTiming = hub.useElementTiming,
        )
        return sessionFromCues(
            hub = hub,
            cues = cues,
            trackLabel = hub.trackTitle.ifBlank { song?.title.orEmpty() },
            trackMediaUri = song?.fileName?.takeIf { it.isNotBlank() },
            trackDurationMs = durationMs,
        )
    }

    /** C-273: Live cues = element clips of the vault choreography the user picked. */
    fun buildFromSelectedChoreography(hub: TrainerUiState, data: AppData?): TrainerSessionUiState {
        val item = com.midnightdancer.feature.choreography.logic.ChoreographyVaultStore
            .byId(hub.selectedChoreographyId)
        val snap = item?.snapshot
        val clips = snap?.elementClips.orEmpty()
        var t = 0f
        val cues = if (clips.isEmpty()) {
            listOf(
                CueAt(
                    sec = 0f,
                    label = hub.selectedChoreographyTitle.ifBlank { item?.title ?: "Хореография" },
                ),
            )
        } else {
            clips.map { clip ->
                val cue = CueAt(sec = t, label = clip.label, mediaUri = clip.mediaUri)
                val speed = clip.speed.takeIf { it > 0.0 } ?: 1.0
                t += ((clip.durationMs.coerceAtLeast(1_000L) / 1000.0) / speed).toFloat()
                    .coerceAtLeast(1f)
                cue
            }
        }
        // C-276: all music blocks from the selected choreography snapshot (frozen at StartDance).
        val musicClips = snap?.musicClips.orEmpty()
        val playlist = musicClips.mapNotNull { clip ->
            val resolvedUri = clip.mediaUri?.takeIf { it.isNotBlank() }
                ?: data?.songs?.firstOrNull { s ->
                    s.title.equals(clip.label, ignoreCase = true) ||
                        (!clip.mediaUri.isNullOrBlank() && s.fileName == clip.mediaUri)
                }?.fileName?.takeIf { it.isNotBlank() }
            resolvedUri?.let {
                MusicClipAt(
                    uri = it,
                    durationMs = clip.durationMs.coerceAtLeast(1_000L),
                    label = clip.label,
                )
            }
        }
        val fallbackSong = data?.songs?.firstOrNull { s ->
            musicClips.firstOrNull()?.let { clip ->
                s.title.equals(clip.label, ignoreCase = true) ||
                    clip.mediaUri?.let { s.fileName == it } == true
            } == true
        }
        val trackDurationMs = when {
            playlist.isNotEmpty() -> playlist.sumOf { it.durationMs }
            else -> ((fallbackSong?.duration ?: 0.0) * 1000.0).toLong().coerceAtLeast(
                ((cues.lastOrNull()?.sec ?: 0f) * 1000f).toLong().coerceAtLeast(60_000L),
            )
        }
        val trackUri = playlist.firstOrNull()?.uri
            ?: fallbackSong?.fileName?.takeIf { it.isNotBlank() }
        return sessionFromCues(
            hub = hub,
            cues = cues,
            trackLabel = hub.selectedChoreographyTitle.ifBlank {
                item?.title ?: fallbackSong?.title.orEmpty()
            },
            trackMediaUri = trackUri,
            trackDurationMs = trackDurationMs,
            musicPlaylist = playlist,
        )
    }

    private fun sessionFromCues(
        hub: TrainerUiState,
        cues: List<CueAt>,
        trackLabel: String,
        trackMediaUri: String?,
        trackDurationMs: Long,
        musicPlaylist: List<MusicClipAt> = emptyList(),
    ): TrainerSessionUiState {
        val runway = cues.take(8).mapIndexed { index, cue ->
            RunwayItem(
                label = cue.label,
                kind = when (index) {
                    0 -> RunwayKind.Active
                    else -> RunwayKind.Next
                },
            )
        }
        val playlist = musicPlaylist.ifEmpty {
            trackMediaUri?.takeIf { it.isNotBlank() }?.let {
                listOf(MusicClipAt(uri = it, durationMs = trackDurationMs.coerceAtLeast(1_000L), label = trackLabel))
            }.orEmpty()
        }
        return TrainerSessionUiState(
            title = "Тренировка",
            trackLabel = trackLabel,
            bpm = hub.bpm,
            isPaused = false,
            phaseIndex = 0,
            phaseTotal = cues.size.coerceAtLeast(1),
            moveTitle = cues.firstOrNull()?.label.orEmpty(),
            moveSubtitle = "",
            runway = runway,
            playheadFraction = 0f,
            cueSchedule = cues,
            trackMediaUri = trackMediaUri,
            musicPlaylist = playlist,
            trackDurationMs = trackDurationMs.coerceAtLeast(1_000L),
            selectedVoice = hub.selectedVoice,
            currentElementMediaUri = cues.firstOrNull()?.mediaUri,
        )
    }

    /**
     * C-199 freestyle:
     * - 1 element → repeat every [interval]
     * - 2+ → random pick each slot
     * C-225: when [useElementTiming], step = element video duration (sec), else [interval].
     */
    fun buildCueSchedule(
        pool: List<Move>,
        interval: Float,
        sessionSec: Float,
        fallbackLabel: String,
        useElementTiming: Boolean = true,
    ): List<CueAt> {
        if (pool.isEmpty()) {
            return listOf(CueAt(sec = 0f, label = fallbackLabel.ifBlank { "Фристайл" }))
        }
        val fallbackStep = interval.coerceAtLeast(1f)
        val rng = Random(pool.size * 31 + (sessionSec * 10).toInt())
        val out = ArrayList<CueAt>()
        var t = 0f
        while (t < sessionSec + 0.01f) {
            val move = if (pool.size == 1) pool[0] else pool[rng.nextInt(pool.size)]
            out += CueAt(sec = t, label = move.name, mediaUri = move.videoUri)
            t += cueStepSec(move, fallbackStep, useElementTiming)
        }
        return out
    }

    /** Per-element spacing when timing flag is on; unknown/0 duration → [fallbackIntervalSec]. */
    fun cueStepSec(
        move: Move,
        fallbackIntervalSec: Float,
        useElementTiming: Boolean,
    ): Float {
        if (!useElementTiming) return fallbackIntervalSec.coerceAtLeast(1f)
        val durMs = ElementDurationCodec.durationMs(move.description)
        return if (durMs > 0L) {
            (durMs / 1000f).coerceAtLeast(1f)
        } else {
            fallbackIntervalSec.coerceAtLeast(1f)
        }
    }

    fun hydrateHubCatalogs(data: AppData, categories: List<String>): TrainerUiState.() -> TrainerUiState =
        {
            val styles = data.danceStyles.map { it.name }.filter { it.isNotBlank() }.distinct()
            val tracks = data.songs.map { it.title }.filter { it.isNotBlank() }.distinct()
            val tags = listOf(TAG_ALL) + categories.ifEmpty { ElementCategoryConfig.DEFAULT_NAMES }
            withChoreographyOptions(
                copy(
                    styleOptions = styles,
                    trackOptions = tracks,
                    tagOptions = tags.distinct(),
                ),
            )
        }

    /** C-273: sync vault titles into hub pickers for the current choreography tag. */
    fun withChoreographyOptions(state: TrainerUiState): TrainerUiState {
        val items = com.midnightdancer.feature.choreography.logic.ChoreographyVaultStore
            .titlesForTag(state.choreographyTag)
        val titles = items.map { it.title }
        val ids = items.associate { it.title to it.id }
        val stillValid = state.selectedChoreographyId.isNotBlank() &&
            items.any { it.id == state.selectedChoreographyId }
        return state.copy(
            choreographyOptions = titles,
            choreographyIdsByTitle = ids,
            selectedChoreographyId = if (stillValid) state.selectedChoreographyId else "",
            selectedChoreographyTitle = if (stillValid) state.selectedChoreographyTitle else "",
        )
    }
}
