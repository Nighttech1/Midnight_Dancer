package com.midnightdancer.feature.choreography.logic

import com.midnightdancer.core.domain.styles.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.midnightdancer.core.domain.settings.ElementCategoryConfig
import com.midnightdancer.core.domain.usecase.LoadLibrary
import com.midnightdancer.core.domain.validation.DomainValidators
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

interface SequenceEditorStubComponent {
    val uiState: StateFlow<SequenceEditorUiState>
    fun onIntent(intent: SequenceEditorIntent)

    /** SKIE-friendly helpers for iOS bridge (C-300). */
    fun togglePlay() = onIntent(SequenceEditorIntent.TogglePlay)
    fun seekToFraction(fraction: Float) = onIntent(SequenceEditorIntent.SeekTo(fraction))
    fun seekStart() = onIntent(SequenceEditorIntent.SeekStart)
    fun seekEnd() = onIntent(SequenceEditorIntent.SeekEnd)
    fun elementChoices(): List<ChoreoCatalogEntry> = ChoreoCatalog.elementsForStyle(uiState.value.style)
    fun addVideoElement(id: String) {
        val entry = elementChoices().firstOrNull { it.id == id } ?: return
        onIntent(SequenceEditorIntent.AddElementClips(listOf(ChoreoCatalog.elementClip(entry))))
    }
    fun saveSequence() = onIntent(SequenceEditorIntent.Save)
    fun setSequenceStyle(style: String) {
        val state = uiState.value
        if (state.elementClips.isEmpty() && state.musicClips.isEmpty()) {
            onIntent(SequenceEditorIntent.SetTitleAndStyle(state.title, style, state.tag))
        }
    }
    fun setSequenceTitle(title: String) {
        val state = uiState.value
        onIntent(SequenceEditorIntent.SetTitleAndStyle(title, state.style, state.tag))
    }
}

class DefaultSequenceEditorStubComponent(
    componentContext: ComponentContext,
    initialTitle: String = "Pro Editor",
    initialSongPlaybackSpeed: Double = 1.0,
    initialStyle: String = ChoreoCatalog.styles.first(),
    initialSnapshot: ChoreographySnapshot? = null,
    initialItemId: String? = null,
    initialTag: String = "",
    initialTagCatalog: List<String> = ElementCategoryConfig.DEFAULT_NAMES,
    /** Persists editor state into the hub list; returns the vault item id. */
    private val onSave: (itemId: String?, snapshot: ChoreographySnapshot) -> String = { id, _ -> id.orEmpty() },
    /** C-272: reload elements/tracks from Room before picker opens. */
    private val onRefreshCatalog: suspend () -> Unit = {},
    private val danceStyles: ManageDanceStyles? = null,
) : SequenceEditorStubComponent, ComponentContext by componentContext {
    private val editorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private data class EditorSnapshot(
        val elementClips: List<EditorClip>,
        val musicClips: List<EditorClip>,
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
    private var currentItemId: String? = initialItemId

    private val _uiState = MutableStateFlow(
        recompute(
            previousDurationMs = 0L,
            state = SequenceEditorUiState(
                title = initialSnapshot?.title ?: initialTitle,
                style = initialSnapshot?.style ?: initialStyle,
                tag = initialSnapshot?.tag?.ifBlank { null } ?: initialTag,
                tagCatalog = initialTagCatalog.ifEmpty { ElementCategoryConfig.DEFAULT_NAMES },
                elementClips = initialSnapshot?.elementClips.orEmpty(),
                musicClips = initialSnapshot?.musicClips.orEmpty(),
                musicVolume = initialSnapshot?.musicVolume ?: 1f,
                previewElementLabel = initialSnapshot?.elementClips?.lastOrNull()?.label,
                songPlaybackSpeed = (initialSnapshot?.songPlaybackSpeed ?: initialSongPlaybackSpeed).coerceIn(
                    DomainValidators.CHOREO_PLAYBACK_SPEED_MIN,
                    DomainValidators.CHOREO_PLAYBACK_SPEED_MAX,
                ),
            ),
        ),
    )
    override val uiState: StateFlow<SequenceEditorUiState> = _uiState.asStateFlow()

    init {
        danceStyles?.let { manager -> editorScope.launch { manager.state.filterNotNull().collect { snapshot ->
            _uiState.update { it.copy(style = snapshot.canonicalId(it.style),
                availableStyles = snapshot.entries.map { row -> row.name },
                styleNamesById = snapshot.entries.associate { row -> row.id to row.name },
                catalogRevision = it.catalogRevision + 1) }
        } } }
        // C-213: leaving without tapping Save still persists the current editor snapshot.
        lifecycle.doOnDestroy {
            flushAutosave()
            editorScope.cancel()
        }
    }

    /** Persist current editor state into the vault (no saveCount flash). */
    fun flushAutosave() {
        val s = _uiState.value
        val snapshot = ChoreographySnapshot(
            title = s.title,
            style = s.style,
            elementClips = s.elementClips,
            musicClips = s.musicClips,
            songPlaybackSpeed = s.songPlaybackSpeed,
            musicVolume = s.musicVolume,
            tag = s.tag,
        )
        currentItemId = onSave(currentItemId, snapshot).ifBlank { currentItemId }
    }

    private fun SequenceEditorUiState.toSnapshot() = EditorSnapshot(
        elementClips = elementClips,
        musicClips = musicClips,
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
            elementClips = elementClips,
            musicClips = musicClips,
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

    private fun mutateTimeline(block: (SequenceEditorUiState) -> SequenceEditorUiState) {
        _uiState.update { current ->
            pushUndoSnapshot(current)
            val next = recompute(current.durationMs, block(current))
            next.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
    }

    /**
     * Moves the playhead; updates [SequenceEditorUiState.previewElementLabel] to the clip under it
     * (4.3.3) and stops playback when the end is reached.
     */
    private fun seek(fraction: Float) {
        _uiState.update { current ->
            val f = fraction.coerceIn(0f, 1f)
            val moved = recompute(current.durationMs, current.copy(playheadFraction = f))
            val atEnd = moved.durationMs > 0L && f >= 1f
            // C-231: past last element → empty preview (do NOT fall back to last label / loop).
            val pastElements = moved.elementClips.isNotEmpty() &&
                moved.playheadMs >= moved.elementsDurationMs
            val clipLabel = if (pastElements) {
                null
            } else {
                moved.elementClips.getOrNull(moved.playheadElementIndex)?.label
            }
            moved.copy(
                isPlaying = if (atEnd) false else moved.isPlaying,
                previewElementLabel = clipLabel,
            )
        }
    }

    override fun onIntent(intent: SequenceEditorIntent) {
        when (intent) {
            SequenceEditorIntent.TogglePlay -> {
                val cur = _uiState.value
                if (!cur.isPlaying && cur.durationMs <= 0L) return
                if (!cur.isPlaying && cur.playheadFraction >= 1f) seek(0f)
                _uiState.update { it.copy(isPlaying = !it.isPlaying) }
            }
            SequenceEditorIntent.NudgeFrameBack ->
                seek(_uiState.value.playheadFraction - 0.02f)
            SequenceEditorIntent.NudgeFrameForward ->
                seek(_uiState.value.playheadFraction + 0.02f)
            SequenceEditorIntent.SeekStart -> seek(0f)
            SequenceEditorIntent.SeekEnd -> seek(1f)
            is SequenceEditorIntent.SeekTo -> seek(intent.fraction)
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
                addElementClips(intent.labels.map { EditorClip(it, ChoreoCatalog.elementDurationMs(it)) })
            is SequenceEditorIntent.AddElementClips -> addElementClips(intent.clips)
            is SequenceEditorIntent.AddMusicTracks ->
                addMusicClips(intent.labels.map { EditorClip(it, ChoreoCatalog.trackDurationMs(it)) })
            is SequenceEditorIntent.AddMusicClips -> addMusicClips(intent.clips)
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
                        previewElementLabel = it.elementClips.getOrNull(intent.index)?.label
                            ?: it.previewElementLabel,
                    )
                }
            is SequenceEditorIntent.SelectMusic ->
                _uiState.update {
                    it.copy(selectedMusicIndex = intent.index, selectedElementIndex = -1)
                }
            is SequenceEditorIntent.ReorderElements ->
                mutateTimeline { state ->
                    val list = state.elementClips.toMutableList()
                    if (intent.from in list.indices && intent.to in list.indices) {
                        val item = list.removeAt(intent.from)
                        list.add(intent.to, item)
                    }
                    state.copy(
                        elementClips = list,
                        selectedElementIndex = intent.to,
                        previewElementLabel = list.getOrNull(intent.to)?.label,
                    )
                }
            is SequenceEditorIntent.ReorderMusic ->
                mutateTimeline { state ->
                    val list = state.musicClips.toMutableList()
                    if (intent.from in list.indices && intent.to in list.indices) {
                        val item = list.removeAt(intent.from)
                        list.add(intent.to, item)
                    }
                    state.copy(musicClips = list, selectedMusicIndex = intent.to)
                }
            SequenceEditorIntent.SplitSelected -> {
                // C-215 / C-224: cut selected lane at playhead; same label on both halves.
                // Prefer element when selected; else music when selectedMusicIndex >= 0.
                val cur = _uiState.value
                val elemIdx = cur.selectedElementIndex
                val musicIdx = cur.selectedMusicIndex
                when {
                    elemIdx in cur.elementClips.indices -> {
                        val local = EditorTrackDurations.clipLocalMs(
                            cur.elementTimelineClips,
                            cur.playheadMs,
                        ) ?: return
                        if (local.first != elemIdx) return
                        val base = cur.elementClips[elemIdx]
                        val safeSpeed = if (base.speed > 0.0) base.speed else 1.0
                        val sourceCutMs = (local.second * safeSpeed).toLong()
                        if (sourceCutMs < 1L || sourceCutMs >= base.durationMs) return
                        mutateTimeline { state ->
                            val list = state.elementClips.toMutableList()
                            val clip = list[elemIdx]
                            val first = sourceCutMs
                            val second = clip.durationMs - first
                            val baseStart = clip.sourceStartMs.coerceAtLeast(0L)
                            list[elemIdx] = clip.copy(durationMs = first, sourceStartMs = baseStart)
                            list.add(
                                elemIdx + 1,
                                clip.copy(
                                    label = clip.label,
                                    durationMs = second,
                                    // C-242: second half continues from cut in source media.
                                    sourceStartMs = baseStart + first,
                                ),
                            )
                            state.copy(elementClips = list, selectedElementIndex = elemIdx + 1)
                        }
                    }
                    musicIdx in cur.musicClips.indices -> {
                        val local = EditorTrackDurations.clipLocalMs(
                            cur.musicTimelineClips,
                            cur.playheadMs,
                        ) ?: return
                        if (local.first != musicIdx) return
                        val base = cur.musicClips[musicIdx]
                        val safeSpeed =
                            if (cur.songPlaybackSpeed > 0.0) cur.songPlaybackSpeed else 1.0
                        val sourceCutMs = (local.second * safeSpeed).toLong()
                        if (sourceCutMs < 1L || sourceCutMs >= base.durationMs) return
                        mutateTimeline { state ->
                            val list = state.musicClips.toMutableList()
                            val clip = list[musicIdx]
                            val first = sourceCutMs
                            val second = clip.durationMs - first
                            val baseStart = clip.sourceStartMs.coerceAtLeast(0L)
                            list[musicIdx] = clip.copy(durationMs = first, sourceStartMs = baseStart)
                            list.add(
                                musicIdx + 1,
                                clip.copy(
                                    label = clip.label,
                                    durationMs = second,
                                    sourceStartMs = baseStart + first,
                                ),
                            )
                            state.copy(musicClips = list, selectedMusicIndex = musicIdx + 1)
                        }
                    }
                    else -> return
                }
            }
            SequenceEditorIntent.DeleteSelected -> {
                val cur = _uiState.value
                val canDeleteElement = cur.selectedElementIndex in cur.elementClips.indices
                val canDeleteMusic = cur.selectedMusicIndex in cur.musicClips.indices
                if (!canDeleteElement && !canDeleteMusic) return
                mutateTimeline { state ->
                    when {
                        state.selectedElementIndex in state.elementClips.indices -> {
                            val list = state.elementClips.toMutableList()
                            list.removeAt(state.selectedElementIndex)
                            state.copy(
                                elementClips = list,
                                selectedElementIndex = -1,
                                previewElementLabel = list.lastOrNull()?.label,
                            )
                        }
                        state.selectedMusicIndex in state.musicClips.indices -> {
                            val list = state.musicClips.toMutableList()
                            list.removeAt(state.selectedMusicIndex)
                            state.copy(musicClips = list, selectedMusicIndex = -1)
                        }
                        else -> state
                    }
                }
            }
            is SequenceEditorIntent.SetMusicVolume ->
                mutateTimeline {
                    it.copy(musicVolume = intent.volume.coerceIn(0f, 1f))
                }
            is SequenceEditorIntent.SetClipSpeed -> {
                // C-216: store speed on the selected element clip so timeline width ∝ duration/speed.
                val speed = intent.speed.coerceIn(
                    DomainValidators.CHOREO_PLAYBACK_SPEED_MIN,
                    DomainValidators.CHOREO_PLAYBACK_SPEED_MAX,
                )
                val idx = _uiState.value.selectedElementIndex
                if (idx !in _uiState.value.elementClips.indices) {
                    mutateTimeline { it.copy(clipSpeed = speed) }
                    return
                }
                mutateTimeline { state ->
                    val list = state.elementClips.toMutableList()
                    list[idx] = list[idx].copy(speed = speed)
                    state.copy(elementClips = list, clipSpeed = speed)
                }
            }
            is SequenceEditorIntent.SetTimelineZoom ->
                _uiState.update { it.copy(timelineZoom = snapTimelineZoom(intent.zoom)) }
            SequenceEditorIntent.RefreshCatalog ->
                editorScope.launch {
                    onRefreshCatalog()
                    _uiState.update { it.copy(catalogRevision = it.catalogRevision + 1) }
                }
            is SequenceEditorIntent.SetTitleAndStyle -> {
                val title = intent.title.trim()
                if (title.isEmpty()) return
                _uiState.update {
                    it.copy(
                        title = title,
                        style = danceStyles?.state?.value?.choiceId(intent.style) ?: intent.style,
                        tag = intent.tag.trim(),
                    )
                }
            }
            SequenceEditorIntent.Save -> {
                val s = _uiState.value
                val snapshot = ChoreographySnapshot(
                    title = s.title,
                    style = s.style,
                    elementClips = s.elementClips,
                    musicClips = s.musicClips,
                    songPlaybackSpeed = s.songPlaybackSpeed,
                    musicVolume = s.musicVolume,
                    tag = s.tag,
                )
                val id = onSave(currentItemId, snapshot)
                if (id.isBlank()) {
                    _uiState.update { it.copy(saveError = "Не удалось сохранить хореографию. Повторите попытку.") }
                } else {
                    currentItemId = id
                    _uiState.update { it.copy(saveCount = it.saveCount + 1, saveError = null) }
                }
            }
        }
    }

    private fun addElementClips(clips: List<EditorClip>) {
        if (clips.isEmpty()) return
        mutateTimeline {
            val next = it.elementClips + clips
            it.copy(
                elementClips = next,
                selectedElementIndex = next.lastIndex,
                selectedMusicIndex = -1,
                previewElementLabel = next.lastOrNull()?.label,
            )
        }
    }

    private fun addMusicClips(clips: List<EditorClip>) {
        if (clips.isEmpty()) return
        mutateTimeline {
            val next = it.musicClips + clips
            it.copy(
                musicClips = next,
                selectedMusicIndex = next.lastIndex,
                selectedElementIndex = -1,
            )
        }
    }

    private companion object {
        /**
         * Timeline total = max(elements, music) (3.7). Keeps the playhead at the same
         * absolute time when the total changes, and refreshes timecode / duration labels.
         */
        fun recompute(previousDurationMs: Long, state: SequenceEditorUiState): SequenceEditorUiState {
            val total = maxOf(state.elementsDurationMs, state.musicDurationMs)
            val posMs = (previousDurationMs * state.playheadFraction).toLong()
            val fraction = when {
                total <= 0L -> 0f
                previousDurationMs <= 0L -> state.playheadFraction.coerceIn(0f, 1f)
                previousDurationMs == total -> state.playheadFraction.coerceIn(0f, 1f)
                else -> (posMs.toFloat() / total).coerceIn(0f, 1f)
            }
            val shownMs = (total * fraction).toLong()
            return state.copy(
                durationMs = total,
                durationLabel = EditorTrackDurations.formatMmSs((total / 1000L).toInt().coerceAtLeast(0)),
                playheadFraction = fraction,
                timecode = EditorTrackDurations.formatTimecode(shownMs),
            )
        }
    }
}

interface ChoreographyHubComponent {
    val uiState: StateFlow<ChoreographyUiState>
    fun onIntent(intent: ChoreographyIntent)
}

private fun nextItemId(items: List<ChoreographyVaultItem>): String {
    val ids = items.map { it.id }.toSet()
    var n = items.size + 1
    while ("c$n" in ids) n++
    return "c$n"
}

/** Pure hub-list reducer shared by hub child + nested root (single source of truth). */
internal fun reduceHubState(state: ChoreographyUiState, intent: ChoreographyIntent, canCreate: Boolean = true): ChoreographyUiState =
    when (intent) {
        ChoreographyIntent.DismissAccessMessage -> state.copy(creationBlockedMessage = null)
        ChoreographyIntent.RefreshPlaceholders -> state.copy(items = DEFAULT_VAULT_ITEMS)
        is ChoreographyIntent.SetSearchQuery -> state.copy(searchQuery = intent.query)
        is ChoreographyIntent.SetStyleFilter -> state.copy(selectedStyle = intent.style)
        is ChoreographyIntent.SetTagFilter -> state.copy(selectedTag = intent.tag)
        is ChoreographyIntent.SetSortMode -> state.copy(sortMode = intent.mode, sortLabel = intent.mode.name)
        is ChoreographyIntent.DeleteItem -> state.copy(items = state.items.filterNot { it.id == intent.id })
        is ChoreographyIntent.SetItemSongPlaybackSpeed -> {
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
        is ChoreographyIntent.CopyItem -> {
            val source = state.items.firstOrNull { it.id == intent.id }
            if (!canCreate && source != null) {
                state.copy(creationBlockedMessage = FREE_CREATION_MESSAGE)
            } else if (source == null) {
                state
            } else {
                val title = ChoreographyCopyNaming.nextCopyTitle(source.title, state.items.map { it.title })
                val id = nextItemId(state.items)
                val copy = source.copy(
                    id = id,
                    title = title,
                    chrId = "CHR-" + (100 + state.items.size + 1).toString().padStart(3, '0'),
                    snapshot = source.snapshot?.copy(title = title),
                )
                val at = state.items.indexOfFirst { it.id == source.id } + 1
                state.copy(items = state.items.toMutableList().apply { add(at, copy) })
            }
        }
        ChoreographyIntent.OpenSequenceEditorStub,
        ChoreographyIntent.CreateChoreography,
        is ChoreographyIntent.CreateNamed,
        is ChoreographyIntent.OpenEditor,
        is ChoreographyIntent.EditItem,
        -> state
    }

/** Insert or replace the vault item for a saved editor snapshot. */
internal fun upsertSnapshot(
    state: ChoreographyUiState,
    itemId: String?,
    snapshot: ChoreographySnapshot,
): Pair<ChoreographyUiState, String> {
    val existing = itemId?.let { id -> state.items.firstOrNull { it.id == id } }
    val id = existing?.id ?: nextItemId(state.items)
    val totalMs = maxOf(
        snapshot.elementClips.sumOf {
            EditorTrackDurations.effectiveMs(it.durationMs, it.speed)
        },
        snapshot.musicClips.sumOf {
            EditorTrackDurations.effectiveMs(it.durationMs, snapshot.songPlaybackSpeed)
        },
    )
    val hasContent = snapshot.elementClips.isNotEmpty() || snapshot.musicClips.isNotEmpty()
    val item = ChoreographyVaultItem(
        id = id,
        title = snapshot.title,
        // C-213: empty → Draft; any clips → Ready.
        status = if (hasContent) VaultStatus.Ready else VaultStatus.Draft,
        style = snapshot.style,
        // Tag chosen in the create dialog (optional); blank = none. Seeded fallback only for legacy items.
        tags = existing?.tags
            ?: ElementCategoryConfig.sanitizeTagsForItem(listOf(snapshot.tag)),
        bpm = existing?.bpm ?: 120,
        durationLabel = EditorTrackDurations.formatMmSs((totalMs / 1000L).toInt()),
        trackName = snapshot.musicClips.firstOrNull()?.label ?: "—",
        chrId = existing?.chrId ?: ("CHR-" + (100 + state.items.size + 1).toString().padStart(3, '0')),
        songPlaybackSpeed = snapshot.songPlaybackSpeed,
        snapshot = snapshot,
    )
    val items = if (existing != null) {
        state.items.map { if (it.id == id) item else it }
    } else {
        listOf(item) + state.items
    }
    return state.copy(items = items) to id
}

class DefaultChoreographyHubComponent(
    componentContext: ComponentContext,
    private val onOpenEditor: (String?) -> Unit,
    private val onCreateNamed: (name: String, style: String, tag: String) -> Unit = { _, _, _ -> },
    private val state: MutableStateFlow<ChoreographyUiState> = MutableStateFlow(ChoreographyUiState()),
    private val creationAccess: com.midnightdancer.core.domain.settings.CreationAccessPolicy? = null,
    private val onReduce: ((ChoreographyIntent) -> Unit)? = null,
) : ChoreographyHubComponent, ComponentContext by componentContext {

    override val uiState: StateFlow<ChoreographyUiState> = state.asStateFlow()

    override fun onIntent(intent: ChoreographyIntent) {
        when (intent) {
            ChoreographyIntent.OpenSequenceEditorStub,
            ChoreographyIntent.CreateChoreography,
            -> onOpenEditor(null)
            is ChoreographyIntent.CreateNamed -> onCreateNamed(intent.name, intent.style, intent.tag)
            is ChoreographyIntent.OpenEditor -> onOpenEditor(intent.id)
            is ChoreographyIntent.EditItem -> onOpenEditor(intent.id)
            else -> if (onReduce != null) onReduce.invoke(intent) else state.update {
                reduceHubState(it, intent, creationAccess?.canCreate(ownChoreographyCount(it.items)) != false)
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
    fun openNewSequenceEditor(title: String, style: String, tag: String = "")
    fun onBack(): Boolean

    /** SKIE-friendly aliases for SwiftUI bridge (C-124 / C-126). */
    fun setSearchQuery(query: String) = onIntent(ChoreographyIntent.SetSearchQuery(query))
    fun createChoreography() = onIntent(ChoreographyIntent.CreateChoreography)
    fun createChoreography(name: String, style: String) =
        onIntent(ChoreographyIntent.CreateNamed(name, style))
    fun createChoreography(name: String, style: String, tag: String) =
        onIntent(ChoreographyIntent.CreateNamed(name, style, tag))
    fun openEditor(id: String) = onIntent(ChoreographyIntent.OpenEditor(id))
    fun deleteItem(id: String) = onIntent(ChoreographyIntent.DeleteItem(id))
    fun copyItem(id: String) = onIntent(ChoreographyIntent.CopyItem(id))

    sealed class Child {
        class Hub(val component: ChoreographyHubComponent) : Child()
        class SequenceEditor(val component: SequenceEditorStubComponent) : Child()
    }
}

class DefaultChoreographyComponent(
    componentContext: ComponentContext,
    private val loadLibrary: LoadLibrary? = null,
    private val vaultPersister: ChoreographyVaultPersister? = null,
    private val creationAccess: com.midnightdancer.core.domain.settings.CreationAccessPolicy? = null,
    private val deletedObserver: com.midnightdancer.core.domain.settings.ChoreographyDeletedObserver? = null,
    private val danceStyles: ManageDanceStyles? = null,
) : ChoreographyComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Single hub list shared with the Hub child and updated by editor Save. */
    private val _uiState = MutableStateFlow(
        ChoreographyUiState(
            items = vaultPersister?.readRaw()?.let { raw ->
                runCatching { decodeVaultItems(raw) }.getOrNull()
            } ?: if (creationAccess == null) DEFAULT_VAULT_ITEMS else emptyList(),
        ),
    )
    override val uiState: StateFlow<ChoreographyUiState> = _uiState.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        ChoreographyVaultStore.replaceAll(_uiState.value.items)
        danceStyles?.let { manager -> scope.launch { manager.state.filterNotNull().collect { snapshot ->
            ChoreoCatalog.replaceStyleCatalog(snapshot)
            val items = normalizeVaultStyles(_uiState.value.items, snapshot)
            _uiState.update { it.copy(items = items, styleNamesById = snapshot.entries.associate { row -> row.id to row.name },
                availableStyles = snapshot.entries.map { row -> row.name },
                selectedStyle = if (it.selectedStyle == "Все стили") it.selectedStyle else snapshot.canonicalId(it.selectedStyle)) }
            ChoreographyVaultStore.replaceAll(items)
            runCatching { vaultPersister?.refreshStyles() }.onFailure {
                _uiState.update { it.copy(creationBlockedMessage = "Не удалось обновить файл хореографий. Стили сохранены; повторное открытие применит их к материалам.") }
            }
        } } }
        scope.launch {
            ChoreographyVaultStore.items.collect { imported ->
                if (imported != _uiState.value.items) _uiState.update { it.copy(items = imported) }
            }
        }
        // C-185/C-272: hydrate picker catalog from Room; keep observing for new elements.
        val load = loadLibrary
        if (load != null) {
            scope.launch {
                runCatching {
                    ChoreoCatalog.replaceFromAppData(load.load())
                }
                load.observe().collect { data ->
                    ChoreoCatalog.replaceFromAppData(data)
                }
            }
        }
    }

    private fun persistVault(items: List<ChoreographyVaultItem>) {
        val items = danceStyles?.state?.value?.let { normalizeVaultStyles(items, it) } ?: items
        _uiState.update { it.copy(items = items) }
        ChoreographyVaultStore.replaceAll(items)
        val persister = vaultPersister ?: return
        runCatching { persister.writeRaw(encodeVaultItems(items)) }
    }

    override val stack: Value<ChildStack<*, ChoreographyComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Hub,
        handleBackButton = true,
        childFactory = ::child,
    )

    override fun onIntent(intent: ChoreographyIntent) {
        val intent = if (intent is ChoreographyIntent.SetStyleFilter && intent.style != "Все стили") {
            intent.copy(style = danceStyles?.state?.value?.choiceId(intent.style) ?: intent.style)
        } else intent
        when (intent) {
            ChoreographyIntent.OpenSequenceEditorStub,
            ChoreographyIntent.CreateChoreography,
            -> openSequenceEditor(null)
            is ChoreographyIntent.CreateNamed -> openNewSequenceEditor(intent.name, intent.style, intent.tag)
            is ChoreographyIntent.OpenEditor -> openSequenceEditor(intent.id)
            is ChoreographyIntent.EditItem -> openSequenceEditor(intent.id)
            else -> {
                _uiState.update { state ->
                    reduceHubState(state, intent, creationAccess?.canCreate(ownChoreographyCount(state.items)) != false)
                }
                persistVault(_uiState.value.items)
                if (intent is ChoreographyIntent.DeleteItem) deletedObserver?.onDeleted(intent.id)
            }
        }
    }

    private fun refreshPickerCatalog() {
        val load = loadLibrary ?: return
        scope.launch {
            runCatching { ChoreoCatalog.replaceFromAppData(load.load()) }
        }
    }

    private suspend fun refreshPickerCatalogSuspend() {
        val load = loadLibrary ?: return
        runCatching { ChoreoCatalog.replaceFromAppData(load.load()) }
    }

    override fun openSequenceEditor(itemId: String?) {
        refreshPickerCatalog()
        if (itemId == null) {
            // C-213: blank create also upserts a Draft before opening the editor.
            openNewSequenceEditor("Новая хореография", ChoreoCatalog.styles.first(), "")
            return
        }
        val item = _uiState.value.items.firstOrNull { it.id == itemId }
        if (item != null && item.snapshot == null && DEFAULT_VAULT_ITEMS.any { it.id == item.id } &&
            creationAccess?.canCreate(ownChoreographyCount(_uiState.value.items)) == false) {
            _uiState.update { it.copy(creationBlockedMessage = FREE_CREATION_MESSAGE) }
            return
        }
        navigation.pushNew(
            Config.SequenceEditor(
                itemId = item?.id ?: itemId,
                title = item?.title ?: "Новая хореография",
                style = item?.style ?: ChoreoCatalog.styles.first(),
                songPlaybackSpeed = item?.songPlaybackSpeed ?: 1.0,
                tag = item?.snapshot?.tag.orEmpty(),
            ),
        )
    }

    override fun openNewSequenceEditor(title: String, style: String, tag: String) {
        val style = danceStyles?.state?.value?.choiceId(style) ?: style
        if (creationAccess?.canCreate(ownChoreographyCount(_uiState.value.items)) == false) {
            _uiState.update { it.copy(creationBlockedMessage = FREE_CREATION_MESSAGE) }
            return
        }
        refreshPickerCatalog()
        val trimmed = title.trim().ifEmpty { "Новая хореография" }
        // C-213: upsert Draft vault item immediately (empty clips OK) before opening editor.
        val draftId = saveFromEditor(
            null,
            ChoreographySnapshot(title = trimmed, style = style, tag = tag),
        )
        navigation.pushNew(
            Config.SequenceEditor(
                itemId = draftId.takeIf { it.isNotBlank() },
                title = trimmed,
                style = style,
                tag = tag,
            ),
        )
    }

    override fun onBack(): Boolean {
        // C-213: autosave active editor snapshot before pop (also covered by doOnDestroy).
        val active = stack.value.active.instance
        if (active is ChoreographyComponent.Child.SequenceEditor) {
            val editor = active.component
            if (editor is DefaultSequenceEditorStubComponent) {
                editor.flushAutosave()
            }
        }
        var handled = false
        navigation.pop { didPop -> handled = didPop }
        return handled
    }

    private fun saveFromEditor(itemId: String?, snapshot: ChoreographySnapshot): String {
        val snapshot = danceStyles?.state?.value?.let { snapshot.copy(style = it.canonicalId(snapshot.style)) } ?: snapshot
        val (next, savedId) = upsertSnapshot(_uiState.value, itemId, snapshot)
        val items = danceStyles?.state?.value?.let { normalizeVaultStyles(next.items, it) } ?: next.items
        // Publish the new snapshot only after the vault write succeeds.
        val persister = vaultPersister
        if (persister != null && runCatching { persister.writeRaw(encodeVaultItems(items)) }.isFailure) return ""
        _uiState.update { it.copy(items = items) }
        ChoreographyVaultStore.replaceAll(items)
        return savedId
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
                        onCreateNamed = ::openNewSequenceEditor,
                        state = _uiState,
                        creationAccess = creationAccess,
                        onReduce = ::onIntent,
                    ),
                )
            is Config.SequenceEditor ->
                ChoreographyComponent.Child.SequenceEditor(
                    DefaultSequenceEditorStubComponent(
                        context,
                        initialTitle = config.title,
                        initialSongPlaybackSpeed = config.songPlaybackSpeed,
                        initialStyle = config.style,
                        danceStyles = danceStyles,
                        initialTag = config.tag,
                        initialTagCatalog = _uiState.value.tagCatalog,
                        initialSnapshot = config.itemId?.let { id ->
                            _uiState.value.items.firstOrNull { it.id == id }?.snapshot
                        },
                        initialItemId = config.itemId,
                        onSave = ::saveFromEditor,
                        onRefreshCatalog = ::refreshPickerCatalogSuspend,
                    ),
                )
        }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Hub : Config

        @Serializable
        data class SequenceEditor(
            val itemId: String? = null,
            val title: String,
            val style: String = "Sensual",
            val songPlaybackSpeed: Double = 1.0,
            val tag: String = "",
        ) : Config
    }
}
