package com.midnightdancer.feature.choreography.logic

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.midnightdancer.core.domain.validation.DomainValidators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

interface SequenceEditorStubComponent {
    val uiState: StateFlow<SequenceEditorUiState>
    fun onIntent(intent: SequenceEditorIntent)
}

class DefaultSequenceEditorStubComponent(
    componentContext: ComponentContext,
    initialTitle: String = "Pro Editor",
    initialSongPlaybackSpeed: Double = 1.0,
) : SequenceEditorStubComponent, ComponentContext by componentContext {
    private data class EditorSnapshot(
        val addedElementLabels: List<String>,
        val addedMusicLabels: List<String>,
        val selectedElementIndex: Int,
        val selectedMusicIndex: Int,
        val musicVolume: Float,
        val clipSpeed: Double,
        val songPlaybackSpeed: Double,
        val durationMs: Long,
        val durationLabel: String,
        val previewElementLabel: String?,
        val timecode: String,
        val playheadFraction: Float,
    )

    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()

    private val _uiState = MutableStateFlow(
        SequenceEditorUiState(
            title = initialTitle,
            songPlaybackSpeed = initialSongPlaybackSpeed.coerceIn(
                DomainValidators.CHOREO_PLAYBACK_SPEED_MIN,
                DomainValidators.CHOREO_PLAYBACK_SPEED_MAX,
            ),
        ),
    )
    override val uiState: StateFlow<SequenceEditorUiState> = _uiState.asStateFlow()

    private fun SequenceEditorUiState.toSnapshot() = EditorSnapshot(
        addedElementLabels = addedElementLabels,
        addedMusicLabels = addedMusicLabels,
        selectedElementIndex = selectedElementIndex,
        selectedMusicIndex = selectedMusicIndex,
        musicVolume = musicVolume,
        clipSpeed = clipSpeed,
        songPlaybackSpeed = songPlaybackSpeed,
        durationMs = durationMs,
        durationLabel = durationLabel,
        previewElementLabel = previewElementLabel,
        timecode = timecode,
        playheadFraction = playheadFraction,
    )

    private fun EditorSnapshot.applyTo(base: SequenceEditorUiState): SequenceEditorUiState =
        base.copy(
            addedElementLabels = addedElementLabels,
            addedMusicLabels = addedMusicLabels,
            selectedElementIndex = selectedElementIndex,
            selectedMusicIndex = selectedMusicIndex,
            musicVolume = musicVolume,
            clipSpeed = clipSpeed,
            songPlaybackSpeed = songPlaybackSpeed,
            durationMs = durationMs,
            durationLabel = durationLabel,
            previewElementLabel = previewElementLabel,
            timecode = timecode,
            playheadFraction = playheadFraction,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )

    private fun pushUndoSnapshot(state: SequenceEditorUiState) {
        undoStack.addLast(state.toSnapshot())
        redoStack.clear()
    }

    private fun withDurationFields(durationMs: Long): Pair<String, String> {
        val label = EditorTrackDurations.formatMmSs((durationMs / 1000L).toInt().coerceAtLeast(0))
        val timecode = EditorTrackDurations.formatTimecode(durationMs)
        return label to timecode
    }

    private fun musicDurationMs(labels: List<String>): Long =
        labels.maxOfOrNull { EditorTrackDurations.secondsFor(it) * 1000L } ?: 0L

    private fun mutateTimeline(block: (SequenceEditorUiState) -> SequenceEditorUiState) {
        _uiState.update { current ->
            pushUndoSnapshot(current)
            val next = block(current)
            next.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
    }

    override fun onIntent(intent: SequenceEditorIntent) {
        when (intent) {
            SequenceEditorIntent.TogglePlay ->
                _uiState.update { it.copy(isPlaying = !it.isPlaying) }
            SequenceEditorIntent.NudgeFrameBack ->
                _uiState.update {
                    it.copy(playheadFraction = (it.playheadFraction - 0.02f).coerceIn(0f, 1f))
                }
            SequenceEditorIntent.NudgeFrameForward ->
                _uiState.update {
                    it.copy(playheadFraction = (it.playheadFraction + 0.02f).coerceIn(0f, 1f))
                }
            SequenceEditorIntent.ToggleSnap ->
                _uiState.update { it.copy(snapToBpm = !it.snapToBpm) }
            SequenceEditorIntent.Undo -> {
                if (undoStack.isEmpty()) return
                _uiState.update { current ->
                    redoStack.addLast(current.toSnapshot())
                    val snap = undoStack.removeLast()
                    snap.applyTo(current)
                }
            }
            SequenceEditorIntent.Redo -> {
                if (redoStack.isEmpty()) return
                _uiState.update { current ->
                    undoStack.addLast(current.toSnapshot())
                    val snap = redoStack.removeLast()
                    snap.applyTo(current)
                }
            }
            is SequenceEditorIntent.AddElements ->
                mutateTimeline { it ->
                    val before = it.addedElementLabels.size
                    val next = (it.addedElementLabels + intent.labels).distinct()
                    val newCount = (next.size - before).coerceAtLeast(0)
                    val musicFloor = musicDurationMs(it.addedMusicLabels)
                    var durationMs = it.durationMs
                    if (newCount > 0) {
                        val extended = it.durationMs + EditorTrackDurations.elementPadMs() * newCount
                        durationMs = maxOf(extended, musicFloor)
                    } else if (durationMs < musicFloor) {
                        durationMs = musicFloor
                    }
                    val (durationLabel, timecode) = withDurationFields(durationMs)
                    it.copy(
                        addedElementLabels = next,
                        selectedElementIndex = next.lastIndex.coerceAtLeast(0),
                        previewElementLabel = next.lastOrNull(),
                        durationMs = durationMs,
                        durationLabel = durationLabel,
                        timecode = timecode,
                    )
                }
            is SequenceEditorIntent.AddMusicTracks ->
                mutateTimeline { it ->
                    val next = (it.addedMusicLabels + intent.labels).distinct()
                    val trackMaxMs = musicDurationMs(next)
                    val durationMs = maxOf(it.durationMs, trackMaxMs)
                    val (durationLabel, timecode) = withDurationFields(durationMs)
                    it.copy(
                        addedMusicLabels = next,
                        selectedMusicIndex = next.lastIndex.coerceAtLeast(0),
                        selectedElementIndex = -1,
                        durationMs = durationMs,
                        durationLabel = durationLabel,
                        timecode = timecode,
                    )
                }
            is SequenceEditorIntent.SetSongPlaybackSpeed -> {
                val next = intent.speed.coerceIn(
                    DomainValidators.CHOREO_PLAYBACK_SPEED_MIN,
                    DomainValidators.CHOREO_PLAYBACK_SPEED_MAX,
                )
                if (next == _uiState.value.songPlaybackSpeed) return
                mutateTimeline { it.copy(songPlaybackSpeed = next) }
            }
            is SequenceEditorIntent.SelectElement ->
                _uiState.update {
                    it.copy(
                        selectedElementIndex = intent.index,
                        selectedMusicIndex = -1,
                        previewElementLabel = it.addedElementLabels.getOrNull(intent.index),
                    )
                }
            is SequenceEditorIntent.SelectMusic ->
                _uiState.update {
                    it.copy(selectedMusicIndex = intent.index, selectedElementIndex = -1)
                }
            is SequenceEditorIntent.ReorderElements ->
                mutateTimeline { state ->
                    val list = state.addedElementLabels.toMutableList()
                    if (intent.from in list.indices && intent.to in list.indices) {
                        val item = list.removeAt(intent.from)
                        list.add(intent.to, item)
                    }
                    state.copy(
                        addedElementLabels = list,
                        selectedElementIndex = intent.to,
                        previewElementLabel = list.getOrNull(intent.to),
                    )
                }
            is SequenceEditorIntent.ReorderMusic ->
                mutateTimeline { state ->
                    val list = state.addedMusicLabels.toMutableList()
                    if (intent.from in list.indices && intent.to in list.indices) {
                        val item = list.removeAt(intent.from)
                        list.add(intent.to, item)
                    }
                    state.copy(addedMusicLabels = list, selectedMusicIndex = intent.to)
                }
            SequenceEditorIntent.SplitSelected -> {
                val idx = _uiState.value.selectedElementIndex
                if (idx !in _uiState.value.addedElementLabels.indices) return
                mutateTimeline { state ->
                    val list = state.addedElementLabels.toMutableList()
                    val base = list[idx]
                    list.add(idx + 1, "$base · b")
                    state.copy(addedElementLabels = list, selectedElementIndex = idx + 1)
                }
            }
            SequenceEditorIntent.DeleteSelected -> {
                val cur = _uiState.value
                val canDeleteElement = cur.selectedElementIndex in cur.addedElementLabels.indices
                val canDeleteMusic = cur.selectedMusicIndex in cur.addedMusicLabels.indices
                if (!canDeleteElement && !canDeleteMusic) return
                mutateTimeline { state ->
                    when {
                        state.selectedElementIndex in state.addedElementLabels.indices -> {
                            val list = state.addedElementLabels.toMutableList()
                            list.removeAt(state.selectedElementIndex)
                            state.copy(
                                addedElementLabels = list,
                                selectedElementIndex = -1,
                                previewElementLabel = list.lastOrNull(),
                            )
                        }
                        state.selectedMusicIndex in state.addedMusicLabels.indices -> {
                            val list = state.addedMusicLabels.toMutableList()
                            list.removeAt(state.selectedMusicIndex)
                            state.copy(addedMusicLabels = list, selectedMusicIndex = -1)
                        }
                        else -> state
                    }
                }
            }
            is SequenceEditorIntent.SetMusicVolume ->
                mutateTimeline {
                    it.copy(musicVolume = intent.volume.coerceIn(0f, 1f))
                }
            is SequenceEditorIntent.SetClipSpeed ->
                mutateTimeline {
                    it.copy(
                        clipSpeed = intent.speed.coerceIn(
                            DomainValidators.CHOREO_PLAYBACK_SPEED_MIN,
                            DomainValidators.CHOREO_PLAYBACK_SPEED_MAX,
                        ),
                    )
                }
        }
    }
}

interface ChoreographyHubComponent {
    val uiState: StateFlow<ChoreographyUiState>
    fun onIntent(intent: ChoreographyIntent)
}

class DefaultChoreographyHubComponent(
    componentContext: ComponentContext,
    private val onOpenEditor: (String?) -> Unit,
) : ChoreographyHubComponent, ComponentContext by componentContext {

    private val _uiState = MutableStateFlow(ChoreographyUiState())
    override val uiState: StateFlow<ChoreographyUiState> = _uiState.asStateFlow()

    override fun onIntent(intent: ChoreographyIntent) {
        when (intent) {
            ChoreographyIntent.OpenSequenceEditorStub,
            ChoreographyIntent.CreateChoreography,
            -> onOpenEditor(null)
            is ChoreographyIntent.OpenEditor -> onOpenEditor(intent.id)
            ChoreographyIntent.RefreshPlaceholders ->
                _uiState.update { it.copy(items = DEFAULT_VAULT_ITEMS) }
            is ChoreographyIntent.SetSearchQuery ->
                _uiState.update { it.copy(searchQuery = intent.query) }
            is ChoreographyIntent.SetStyleFilter ->
                _uiState.update { it.copy(selectedStyle = intent.style) }
            is ChoreographyIntent.SetTagFilter ->
                _uiState.update { it.copy(selectedTag = intent.tag) }
            is ChoreographyIntent.SetSortMode ->
                _uiState.update {
                    it.copy(
                        sortMode = intent.mode,
                        sortLabel = intent.mode.name,
                    )
                }
            is ChoreographyIntent.DeleteItem ->
                _uiState.update { state ->
                    state.copy(items = state.items.filterNot { it.id == intent.id })
                }
            is ChoreographyIntent.EditItem -> onOpenEditor(intent.id)
            is ChoreographyIntent.SetItemSongPlaybackSpeed ->
                _uiState.update { state ->
                    val speed = intent.speed.coerceIn(
                        DomainValidators.CHOREO_PLAYBACK_SPEED_MIN,
                        DomainValidators.CHOREO_PLAYBACK_SPEED_MAX,
                    )
                    state.copy(
                        items = state.items.map { item ->
                            if (item.id == intent.id) item.copy(songPlaybackSpeed = speed) else item
                        },
                    )
                }
        }
    }
}

/** Nested stack: Hub (Kinetic Vault) → SequenceEditor (Pro Editor). */
interface ChoreographyComponent {
    val stack: Value<ChildStack<*, Child>>
    val uiState: StateFlow<ChoreographyUiState>
    fun onIntent(intent: ChoreographyIntent)
    fun openSequenceEditor(itemId: String? = null)
    fun onBack(): Boolean

    /** SKIE-friendly aliases for SwiftUI bridge (C-124 / C-126). */
    fun setSearchQuery(query: String) = onIntent(ChoreographyIntent.SetSearchQuery(query))
    fun createChoreography() = onIntent(ChoreographyIntent.CreateChoreography)
    fun openEditor(id: String) = onIntent(ChoreographyIntent.OpenEditor(id))
    fun deleteItem(id: String) = onIntent(ChoreographyIntent.DeleteItem(id))

    sealed class Child {
        class Hub(val component: ChoreographyHubComponent) : Child()
        class SequenceEditor(val component: SequenceEditorStubComponent) : Child()
    }
}

class DefaultChoreographyComponent(
    componentContext: ComponentContext,
) : ChoreographyComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    private val _uiState = MutableStateFlow(ChoreographyUiState())
    override val uiState: StateFlow<ChoreographyUiState> = _uiState.asStateFlow()

    override val stack: Value<ChildStack<*, ChoreographyComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Hub,
        handleBackButton = true,
        childFactory = ::child,
    )

    override fun onIntent(intent: ChoreographyIntent) {
        when (intent) {
            ChoreographyIntent.OpenSequenceEditorStub,
            ChoreographyIntent.CreateChoreography,
            -> openSequenceEditor(null)
            is ChoreographyIntent.OpenEditor -> openSequenceEditor(intent.id)
            ChoreographyIntent.RefreshPlaceholders,
            is ChoreographyIntent.SetSearchQuery,
            is ChoreographyIntent.SetStyleFilter,
            is ChoreographyIntent.SetTagFilter,
            is ChoreographyIntent.SetSortMode,
            is ChoreographyIntent.DeleteItem,
            is ChoreographyIntent.EditItem,
            is ChoreographyIntent.SetItemSongPlaybackSpeed,
            -> {
                _uiState.update { state ->
                    when (intent) {
                        ChoreographyIntent.RefreshPlaceholders ->
                            state.copy(items = DEFAULT_VAULT_ITEMS)
                        is ChoreographyIntent.SetSearchQuery ->
                            state.copy(searchQuery = intent.query)
                        is ChoreographyIntent.SetStyleFilter ->
                            state.copy(selectedStyle = intent.style)
                        is ChoreographyIntent.SetTagFilter ->
                            state.copy(selectedTag = intent.tag)
                        is ChoreographyIntent.SetSortMode ->
                            state.copy(sortMode = intent.mode, sortLabel = intent.mode.name)
                        is ChoreographyIntent.DeleteItem ->
                            state.copy(items = state.items.filterNot { it.id == intent.id })
                        is ChoreographyIntent.SetItemSongPlaybackSpeed -> {
                            val speed = intent.speed.coerceIn(
                                DomainValidators.CHOREO_PLAYBACK_SPEED_MIN,
                                DomainValidators.CHOREO_PLAYBACK_SPEED_MAX,
                            )
                            state.copy(
                                items = state.items.map { item ->
                                    if (item.id == intent.id) {
                                        item.copy(songPlaybackSpeed = speed)
                                    } else {
                                        item
                                    }
                                },
                            )
                        }
                        else -> state
                    }
                }
                val hub = stack.value.active.instance as? ChoreographyComponent.Child.Hub
                hub?.component?.onIntent(intent)
            }
        }
    }

    override fun openSequenceEditor(itemId: String?) {
        val item = itemId?.let { id -> _uiState.value.items.firstOrNull { it.id == id } }
        val title = item?.title ?: "Новая хореография"
        navigation.pushNew(
            Config.SequenceEditor(
                title = title,
                songPlaybackSpeed = item?.songPlaybackSpeed ?: 1.0,
            ),
        )
    }

    override fun onBack(): Boolean {
        var handled = false
        navigation.pop { didPop -> handled = didPop }
        return handled
    }

    private fun child(
        config: Config,
        context: ComponentContext,
    ): ChoreographyComponent.Child =
        when (config) {
            Config.Hub ->
                ChoreographyComponent.Child.Hub(
                    DefaultChoreographyHubComponent(
                        componentContext = context,
                        onOpenEditor = ::openSequenceEditor,
                    ),
                )
            is Config.SequenceEditor ->
                ChoreographyComponent.Child.SequenceEditor(
                    DefaultSequenceEditorStubComponent(
                        context,
                        initialTitle = config.title,
                        initialSongPlaybackSpeed = config.songPlaybackSpeed,
                    ),
                )
        }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Hub : Config

        @Serializable
        data class SequenceEditor(
            val title: String,
            val songPlaybackSpeed: Double = 1.0,
        ) : Config
    }
}
