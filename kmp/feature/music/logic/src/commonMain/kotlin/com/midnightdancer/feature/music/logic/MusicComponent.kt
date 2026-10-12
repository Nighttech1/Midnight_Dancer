package com.midnightdancer.feature.music.logic

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.midnightdancer.core.domain.debug.EdgeStressUiBridge
import com.midnightdancer.core.domain.settings.ElementCategoryConfig
import com.midnightdancer.core.domain.validation.DomainValidators
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

class DefaultMusicComponent(
    componentContext: ComponentContext,
) : MusicComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(MusicUiState())
    override val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
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
            MusicIntent.RefreshPlaceholders ->
                _uiState.update { it.copy(tracks = DEFAULT_TRACKS, editingTrackId = null) }
            is MusicIntent.SelectTrack ->
                _uiState.update { state ->
                    val already = state.tracks.any { it.id == intent.id && it.isPlaying }
                    if (already) {
                        state.copy(
                            tracks = state.tracks.map { track ->
                                track.copy(isPlaying = false, progress = 0f)
                            },
                        )
                    } else {
                        state.copy(
                            tracks = state.tracks.map { track ->
                                track.copy(
                                    isPlaying = track.id == intent.id,
                                    progress =
                                        if (track.id == intent.id) {
                                            track.progress.coerceAtLeast(0.08f)
                                        } else {
                                            0f
                                        },
                                )
                            },
                        )
                    }
                }
            is MusicIntent.StopTrack ->
                _uiState.update { state ->
                    state.copy(
                        tracks = state.tracks.map { track ->
                            if (track.id == intent.id) {
                                track.copy(isPlaying = false, progress = 0f)
                            } else {
                                track
                            }
                        },
                    )
                }
            is MusicIntent.SetStyleFilter ->
                _uiState.update { it.copy(selectedStyle = intent.style) }
            is MusicIntent.SetLevelFilter ->
                _uiState.update { it.copy(selectedLevel = intent.level) }
            is MusicIntent.SetChip ->
                _uiState.update { it.copy(activeChip = intent.chip) }
            is MusicIntent.DeleteTrack ->
                _uiState.update { state ->
                    state.copy(
                        tracks = state.tracks.filterNot { it.id == intent.id },
                        editingTrackId =
                            if (state.editingTrackId == intent.id) null else state.editingTrackId,
                    )
                }
            is MusicIntent.EditTrack ->
                _uiState.update { it.copy(editingTrackId = intent.id) }
            MusicIntent.BeginUpload ->
                _uiState.update { state ->
                    val id = "upload-${state.tracks.size + 1}"
                    val draft = MusicTrack(
                        id = id,
                        title = "Новый трек",
                        style = state.styleCatalog.firstOrNull() ?: "Бачата",
                        level = state.tagCatalog.firstOrNull() ?: "Базовый",
                        tags = ElementCategoryConfig.sanitizeTagsForItem(
                            listOf(state.tagCatalog.firstOrNull() ?: "Базовый"),
                        ),
                        bpm = 120,
                        durationLabel = "00:00",
                        weightLabel = "0 MB",
                    )
                    state.copy(
                        tracks = listOf(draft) + state.tracks,
                        editingTrackId = id,
                    )
                }
            MusicIntent.CloseEdit ->
                _uiState.update { it.copy(editingTrackId = null) }
            is MusicIntent.SetSearchQuery ->
                _uiState.update { it.copy(searchQuery = intent.query) }
            is MusicIntent.SetSortMode ->
                _uiState.update { it.copy(sortMode = intent.mode) }
            is MusicIntent.SaveTrackEdit ->
                _uiState.update { state ->
                    val speed = intent.basePlaybackSpeed.coerceIn(
                        DomainValidators.PLAYBACK_SPEED_MIN,
                        DomainValidators.PLAYBACK_SPEED_MAX,
                    )
                    val tags = ElementCategoryConfig.sanitizeTagsForItem(intent.tags)
                    state.copy(
                        editingTrackId = null,
                        tracks = state.tracks.map { track ->
                            if (track.id != intent.id) {
                                track
                            } else {
                                track.copy(
                                    style = intent.style,
                                    tags = tags,
                                    level = tags.firstOrNull() ?: track.level,
                                    basePlaybackSpeed = speed,
                                )
                            }
                        },
                    )
                }
        }
    }
}
