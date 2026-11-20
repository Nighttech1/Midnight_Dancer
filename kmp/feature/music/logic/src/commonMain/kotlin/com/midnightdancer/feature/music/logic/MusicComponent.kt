package com.midnightdancer.feature.music.logic

import com.midnightdancer.core.domain.styles.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.midnightdancer.core.domain.debug.DeletedTrackBus
import com.midnightdancer.core.domain.debug.EdgeStressUiBridge
import com.midnightdancer.core.domain.settings.ElementCategoryConfig
import com.midnightdancer.core.domain.usecase.LoadLibrary
import com.midnightdancer.core.domain.usecase.SaveAppData
import com.midnightdancer.core.domain.validation.DomainValidators
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface MusicComponent {
    val uiState: StateFlow<MusicUiState>
    fun onIntent(intent: MusicIntent)

    /** SKIE-friendly aliases for SwiftUI bridge (C-124). */
    fun selectTrack(id: String) = onIntent(MusicIntent.SelectTrack(id))
    fun stopTrack(id: String) = onIntent(MusicIntent.StopTrack(id))
    fun setSearchQuery(query: String) = onIntent(MusicIntent.SetSearchQuery(query))
    fun beginUpload() = onIntent(MusicIntent.BeginUpload)
    fun closeEdit() = onIntent(MusicIntent.CloseEdit)
    fun editTrack(id: String) = onIntent(MusicIntent.EditTrack(id))
    fun deleteTrack(id: String) = onIntent(MusicIntent.DeleteTrack(id))
}

/** "03:08" × 0.5 → "01:34". Unparseable labels fall back to "00:00". */
internal fun positionLabelFor(durationLabel: String, progress: Float): String {
    val parts = durationLabel.split(":")
    val totalSec = if (parts.size == 2) {
        (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
    } else {
        0
    }
    val sec = (totalSec * progress.coerceIn(0f, 1f)).toInt()
    return "${(sec / 60).toString().padStart(2, '0')}:${(sec % 60).toString().padStart(2, '0')}"
}

/** C-134 / C-152: new draft track on top of the list + edit dialog open; tag optional. */
internal fun formatDurationLabel(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L).toInt()
    return "${(totalSec / 60).toString().padStart(2, '0')}:${(totalSec % 60).toString().padStart(2, '0')}"
}

internal fun MusicUiState.withDraftTrack(
    title: String,
    style: String,
    mediaUri: String? = null,
): MusicUiState {
    var n = tracks.size + 1
    while (tracks.any { it.id == "upload-$n" }) n++
    val id = "upload-$n"
    val draft = MusicTrack(
        id = id,
        title = title,
        style = style,
        level = "",
        tags = emptyList(),
        bpm = 120,
        durationLabel = "00:00",
        weightLabel = "0 MB",
        mediaUri = mediaUri,
    )
    return copy(tracks = listOf(draft) + tracks, editingTrackId = id)
}

class DefaultMusicComponent(
    componentContext: ComponentContext,
    private val loadLibrary: LoadLibrary? = null,
    private val saveAppData: SaveAppData? = null,
    private val danceStyles: ManageDanceStyles? = null,
) : MusicComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(MusicUiState())
    override val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        hydrateTracksFromRoom()
        danceStyles?.let { manager -> scope.launch { manager.state.filterNotNull().collect { snapshot ->
            _uiState.update { current -> current.copy(styleCatalog = snapshot.entries.map { it.name },
                styleNamesById = snapshot.entries.associate { it.id to it.name },
                selectedStyle = if (current.selectedStyle == "Все стили") current.selectedStyle else snapshot.canonicalId(current.selectedStyle),
                tracks = current.tracks.map { it.copy(style = snapshot.canonicalId(it.style)) }) }
        } } }
        scope.launch {
            EdgeStressUiBridge.requests.collectLatest { request ->
                if (request == null || request.trackCount <= 0) return@collectLatest
                val stubs = buildEdgeStressTracks(request.trackCount)
                _uiState.update { state ->
                    val kept = state.tracks.filterNot { it.id.startsWith("edge-stress-song-") }
                    state.copy(tracks = stubs + kept, editingTrackId = null)
                }
            }
        }
    }

    override fun onIntent(intent: MusicIntent) {
        when (intent) {
            MusicIntent.RefreshPlaceholders -> {
                _uiState.update { it.copy(tracks = DEFAULT_TRACKS, editingTrackId = null) }
                hydrateTracksFromRoom()
            }
            is MusicIntent.SelectTrack ->
                _uiState.update { state ->
                    val already = state.tracks.any { it.id == intent.id && it.isPlaying }
                    if (already) {
                        // C-152: pause keeps the track selected + its position.
                        state.copy(
                            selectedTrackId = intent.id,
                            tracks = state.tracks.map { track ->
                                if (track.id == intent.id) track.copy(isPlaying = false) else track
                            },
                        )
                    } else {
                        state.copy(
                            selectedTrackId = intent.id,
                            tracks = state.tracks.map { track ->
                                track.copy(
                                    isPlaying = track.id == intent.id,
                                    progress =
                                        if (track.id == intent.id) track.progress else 0f,
                                    positionLabel =
                                        if (track.id == intent.id) track.positionLabel else "00:00",
                                )
                            },
                        )
                    }
                }
            is MusicIntent.StopTrack ->
                _uiState.update { state ->
                    // C-152: stop = pause; track stays selected, position is kept.
                    state.copy(
                        selectedTrackId = intent.id,
                        tracks = state.tracks.map { track ->
                            if (track.id == intent.id) track.copy(isPlaying = false) else track
                        },
                    )
                }
            is MusicIntent.SeekTrack ->
                _uiState.update { state ->
                    state.copy(
                        tracks = state.tracks.map { track ->
                            if (track.id != intent.id) {
                                track
                            } else {
                                val p = intent.progress.coerceIn(0f, 1f)
                                track.copy(
                                    progress = p,
                                    positionLabel = positionLabelFor(track.durationLabel, p),
                                )
                            }
                        },
                    )
                }
            is MusicIntent.SetStyleFilter ->
                _uiState.update { it.copy(selectedStyle = if (intent.style == "Все стили") intent.style else danceStyles?.state?.value?.choiceId(intent.style) ?: intent.style) }
            is MusicIntent.SetLevelFilter ->
                _uiState.update { it.copy(selectedLevel = intent.level) }
            is MusicIntent.SetChip ->
                _uiState.update { it.copy(activeChip = intent.chip) }
            is MusicIntent.DeleteTrack -> {
                // C-132 / 2.8: tell Choreography to drop stub refs to this track.
                _uiState.value.tracks.firstOrNull { it.id == intent.id }?.let { gone ->
                    DeletedTrackBus.publish(gone.id, gone.title)
                }
                _uiState.update { state ->
                    state.copy(
                        tracks = state.tracks.filterNot { it.id == intent.id },
                        editingTrackId =
                            if (state.editingTrackId == intent.id) null else state.editingTrackId,
                        selectedTrackId =
                            if (state.selectedTrackId == intent.id) null else state.selectedTrackId,
                    )
                }
                persistDelete(intent.id)
            }
            is MusicIntent.EditTrack ->
                _uiState.update { if (it.saving) it else it.copy(editingTrackId = intent.id, operationError = null) }
            MusicIntent.BeginUpload ->
                _uiState.update { state ->
                    state.withDraftTrack(
                        title = "Новый трек",
                        style = danceStyles?.state?.value?.entries?.firstOrNull()?.id ?: state.styleCatalog.firstOrNull() ?: "Бачата",
                    )
                }
            is MusicIntent.AddPickedTrack -> {
                val before = _uiState.value
                if (!before.saving) {
                    val prepared = before.withDraftTrack(
                        title = intent.title.trim().ifBlank { "Новый трек" },
                        style = danceStyles?.state?.value?.entries?.firstOrNull()?.id.orEmpty(),
                        mediaUri = intent.mediaUri,
                    )
                    val track = prepared.tracks.first { it.id == prepared.editingTrackId }
                    if (saveAppData == null) { _uiState.value = prepared; return }

                    _uiState.update { it.copy(saving = true, operationError = null) }
                    scope.launch {
                        try {
                            withContext(NonCancellable + Dispatchers.Default) { saveAppData?.upsertSong(track.toSong()) }
                            _uiState.update { latest ->
                                val committed = track.copy(style = danceStyles?.state?.value?.canonicalId(track.style) ?: track.style)
                                latest.copy(tracks = listOf(committed) + latest.tracks.filterNot { it.id == committed.id },
                                    editingTrackId = committed.id, saving = false, operationError = null)
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
                        } catch (_: Exception) {
                            _uiState.update { it.copy(saving = false, operationError = "Не удалось сохранить музыку. Прежние материалы не изменены. Попробуйте снова.") }
                        }
                    }
                }
            }
            is MusicIntent.UpdateTrackDuration -> {
                var updated: MusicTrack? = null
                _uiState.update { state ->
                    val label = formatDurationLabel(intent.durationMs)
                    state.copy(
                        tracks = state.tracks.map { track ->
                            if (track.id != intent.id) {
                                track
                            } else {
                                track.copy(
                                    durationLabel = label,
                                    positionLabel = positionLabelFor(label, track.progress),
                                ).also { updated = it }
                            }
                        },
                    )
                }
                updated?.let(::persistTrack)
            }
            MusicIntent.CloseEdit ->
                _uiState.update { if (it.saving) it else it.copy(editingTrackId = null) }
            is MusicIntent.SetSearchQuery ->
                _uiState.update { it.copy(searchQuery = intent.query) }
            is MusicIntent.SetSortMode ->
                _uiState.update { it.copy(sortMode = intent.mode) }
            is MusicIntent.SaveTrackEdit -> {
                val before = _uiState.value
                if (before.saving) return
                val old = before.tracks.firstOrNull { it.id == intent.id } ?: return
                val tags = ElementCategoryConfig.sanitizeTagsForItem(intent.tags)
                val saved = old.copy(title = intent.title.trim().ifBlank { old.title },
                    style = danceStyles?.state?.value?.choiceId(intent.style) ?: intent.style,
                    tags = tags, level = tags.firstOrNull().orEmpty(),
                    basePlaybackSpeed = intent.basePlaybackSpeed.coerceIn(DomainValidators.PLAYBACK_SPEED_MIN, DomainValidators.PLAYBACK_SPEED_MAX))
                if (saveAppData == null) {
                    _uiState.update { current -> current.copy(editingTrackId = null,
                        tracks = current.tracks.map { if (it.id == saved.id) saved else it }) }
                } else {
                    _uiState.update { it.copy(saving = true, operationError = null) }
                    scope.launch {
                        try {
                            withContext(NonCancellable + Dispatchers.Default) { saveAppData.upsertSong(saved.toSong()) }
                            _uiState.update { current -> current.copy(editingTrackId = null, saving = false,
                                tracks = current.tracks.map { row -> if (row.id == saved.id) saved.copy(style = danceStyles?.state?.value?.canonicalId(saved.style) ?: saved.style) else row }) }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
                        } catch (_: Exception) { _uiState.update { it.copy(saving = false,
                            operationError = "Не удалось записать изменения музыки. Прежние данные сохранены. Попробуйте снова.") } }
                    }
                }
            }
        }
    }

    private fun hydrateTracksFromRoom() {
        val load = loadLibrary ?: return
        scope.launch {
            val catalogFlow = danceStyles?.let { manager -> combine(load.observe(), manager.state.filterNotNull()) { data, _ -> data } } ?: load.observe()
            catalogFlow.collect { data ->
            _uiState.update { current ->
                current.copy(
                    tracks = data.toMusicTracks(previous = current.tracks).map { track ->
                        danceStyles?.state?.value?.let { track.copy(style = it.canonicalId(track.style)) } ?: track
                    },
                    editingTrackId = current.editingTrackId?.takeIf { id ->
                        data.songs.any { it.id == id } || current.tracks.any { it.id == id }
                    },
                )
            }
            }
        }
    }

    private fun persistTrack(track: MusicTrack) {
        val save = saveAppData ?: return
        scope.launch {
            try {
                withContext(NonCancellable + Dispatchers.Default) { save.upsertSong(track.toSong()) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(operationError = "Не удалось записать изменения музыки. Попробуйте снова.") }
            }
        }
    }

    private fun persistDelete(id: String) {
        val save = saveAppData ?: return
        scope.launch {
            withContext(NonCancellable + Dispatchers.Default) {
                save.deleteSong(id)
            }
        }
    }
}
