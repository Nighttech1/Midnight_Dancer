package com.midnightdancer.feature.choreography.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChoreographyLogicSmokeTest {

    // The Decompose component runs on Dispatchers.Main; android-unit (Windows) needs it set to a
    // test dispatcher. Without this, these tests fail once a sibling test has called resetMain().
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    @Test
    fun intent_openSequenceEditorStub_pushesNestedChild() {
        val lifecycle = LifecycleRegistry()
        val component = DefaultChoreographyComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        assertEquals("Хореография", component.uiState.value.title)
        assertTrue(component.stack.value.active.instance is ChoreographyComponent.Child.Hub)

        component.onIntent(ChoreographyIntent.OpenSequenceEditorStub)
        assertTrue(
            component.stack.value.active.instance is ChoreographyComponent.Child.SequenceEditor,
        )
        assertTrue(component.onBack())
        assertTrue(component.stack.value.active.instance is ChoreographyComponent.Child.Hub)
    }

    @Test
    fun addMusicTracks_setsDuration_andUndoRestores() {
        val lifecycle = LifecycleRegistry()
        val editor = DefaultSequenceEditorStubComponent(
            DefaultComponentContext(lifecycle = lifecycle),
            initialTitle = "Test Choreo",
        )
        lifecycle.resume()

        assertEquals(0L, editor.uiState.value.durationMs)
        assertFalse(editor.uiState.value.canUndo)

        editor.onIntent(SequenceEditorIntent.AddMusicTracks(listOf("Charles Luis - Ocasión")))
        val afterAdd = editor.uiState.value
        assertTrue(afterAdd.durationMs > 0L)
        assertEquals(listOf("Charles Luis - Ocasión"), afterAdd.addedMusicLabels)
        assertTrue(afterAdd.canUndo)

        editor.onIntent(SequenceEditorIntent.Undo)
        val afterUndo = editor.uiState.value
        assertEquals(0L, afterUndo.durationMs)
        assertTrue(afterUndo.addedMusicLabels.isEmpty())
        assertTrue(afterUndo.canRedo)
        assertFalse(afterUndo.canUndo)
    }

    @Test
    fun copyNaming_incrementsCopyPrefix() {
        val existing = listOf("Salsa")
        assertEquals("копия - Salsa", ChoreographyCopyNaming.nextCopyTitle("Salsa", existing))
        assertEquals(
            "копия1 - Salsa",
            ChoreographyCopyNaming.nextCopyTitle("Salsa", existing + "копия - Salsa"),
        )
        assertEquals(
            "копия2 - Salsa",
            ChoreographyCopyNaming.nextCopyTitle(
                "копия - Salsa",
                existing + "копия - Salsa" + "копия1 - Salsa",
            ),
        )
    }

    @Test
    fun timeline_totalIsMaxOfElementsAndMusic_andSeekJumps() {
        val lifecycle = LifecycleRegistry()
        val editor = DefaultSequenceEditorStubComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        editor.onIntent(SequenceEditorIntent.AddMusicClips(listOf(EditorClip("Track", 200_000L))))
        assertEquals(200_000L, editor.uiState.value.durationMs)
        editor.onIntent(
            SequenceEditorIntent.AddElementClips(
                listOf(EditorClip("A", 40_000L), EditorClip("B", 60_000L)),
            ),
        )
        // Music keeps its own length; total = max(100s elements, 200s music).
        assertEquals(200_000L, editor.uiState.value.durationMs)
        assertEquals(200_000L, editor.uiState.value.musicDurationMs)

        editor.onIntent(SequenceEditorIntent.SeekEnd)
        assertEquals(1f, editor.uiState.value.playheadFraction)
        editor.onIntent(SequenceEditorIntent.SeekStart)
        assertEquals(0f, editor.uiState.value.playheadFraction)
    }

    @Test
    fun formatTimecode_usesMmSsCentiseconds() {
        assertEquals("00.00.00", EditorTrackDurations.formatTimecode(0L))
        assertEquals("01.23.45", EditorTrackDurations.formatTimecode(83_450L))
        assertEquals("01.23.99", EditorTrackDurations.formatTimecode(83_999L))
        assertEquals("00.00.00", EditorTrackDurations.formatTimecode(-5L))
        assertEquals("01.30.00", EditorTrackDurations.formatTimecode(90_000L))
    }

    @Test
    fun durationTimecode_matchesTotal() {
        val lifecycle = LifecycleRegistry()
        val editor = DefaultSequenceEditorStubComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()
        editor.onIntent(SequenceEditorIntent.AddMusicClips(listOf(EditorClip("Track", 90_000L))))
        assertEquals("01.30.00", editor.uiState.value.durationTimecode)
        assertEquals("00.00.00", editor.uiState.value.timecode)
    }

    @Test
    fun musicEffectiveDuration_shrinksWithSongSpeed() {
        val lifecycle = LifecycleRegistry()
        val editor = DefaultSequenceEditorStubComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()
        editor.onIntent(SequenceEditorIntent.AddMusicClips(listOf(EditorClip("Track", 200_000L))))
        assertEquals(200_000L, editor.uiState.value.durationMs)

        editor.onIntent(SequenceEditorIntent.SetSongPlaybackSpeed(2.0))
        val fast = editor.uiState.value
        assertEquals(100_000L, fast.musicDurationMs)
        assertEquals(listOf(100_000L), fast.musicTimelineClips.map { it.durationMs })
        assertEquals(100_000L, fast.durationMs)
        // Source clip untouched.
        assertEquals(200_000L, fast.musicClips.single().durationMs)

        editor.onIntent(SequenceEditorIntent.SetSongPlaybackSpeed(0.5))
        assertEquals(400_000L, editor.uiState.value.musicDurationMs)
    }

    @Test
    fun playhead_mapsToElementClip_andStopsAtEnd() {
        val lifecycle = LifecycleRegistry()
        val editor = DefaultSequenceEditorStubComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()
        editor.onIntent(
            SequenceEditorIntent.AddElementClips(
                listOf(EditorClip("A", 40_000L), EditorClip("B", 60_000L)),
            ),
        )
        editor.onIntent(SequenceEditorIntent.SeekTo(0.1f))
        assertEquals("A", editor.uiState.value.previewElementLabel)
        assertEquals(0, editor.uiState.value.playheadElementIndex)

        editor.onIntent(SequenceEditorIntent.SeekTo(0.5f))
        assertEquals("B", editor.uiState.value.previewElementLabel)
        assertEquals(1, editor.uiState.value.playheadElementIndex)

        editor.onIntent(SequenceEditorIntent.SeekStart)
        editor.onIntent(SequenceEditorIntent.TogglePlay)
        assertTrue(editor.uiState.value.isPlaying)
        editor.onIntent(SequenceEditorIntent.SeekTo(1f))
        assertFalse(editor.uiState.value.isPlaying)
        assertEquals(1f, editor.uiState.value.playheadFraction)

        // Play at the end restarts from 0.
        editor.onIntent(SequenceEditorIntent.TogglePlay)
        assertTrue(editor.uiState.value.isPlaying)
        assertEquals(0f, editor.uiState.value.playheadFraction)
        assertEquals("A", editor.uiState.value.previewElementLabel)
    }

    @Test
    fun togglePlay_ignoredWhenTimelineEmpty() {
        val lifecycle = LifecycleRegistry()
        val editor = DefaultSequenceEditorStubComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()
        editor.onIntent(SequenceEditorIntent.TogglePlay)
        assertFalse(editor.uiState.value.isPlaying)
    }

    @Test
    fun save_persistsSnapshotIntoHub_andCopyKeepsClips() {
        val lifecycle = LifecycleRegistry()
        val component = DefaultChoreographyComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        component.onIntent(ChoreographyIntent.CreateNamed("My Flow", "Bachata"))
        val editor = (component.stack.value.active.instance as ChoreographyComponent.Child.SequenceEditor).component
        editor.onIntent(SequenceEditorIntent.AddElementClips(listOf(EditorClip("Gone later", 30_000L))))
        editor.onIntent(SequenceEditorIntent.Save)

        val saved = component.uiState.value.items.first { it.title == "My Flow" }
        assertEquals("Bachata", saved.style)
        assertEquals(listOf(EditorClip("Gone later", 30_000L)), saved.snapshot?.elementClips)

        component.onIntent(ChoreographyIntent.CopyItem(saved.id))
        val copy = component.uiState.value.items.first { it.title == "копия - My Flow" }
        assertEquals(saved.snapshot?.elementClips, copy.snapshot?.elementClips)
    }

    @Test
    fun createNamed_withTag_persistsTagOnVaultItem() {
        val lifecycle = LifecycleRegistry()
        val component = DefaultChoreographyComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        component.onIntent(ChoreographyIntent.CreateNamed("Tagged", "Bachata", "Соло"))
        val editor = (component.stack.value.active.instance as ChoreographyComponent.Child.SequenceEditor).component
        assertEquals("Соло", editor.uiState.value.tag)
        editor.onIntent(SequenceEditorIntent.Save)

        val saved = component.uiState.value.items.first { it.title == "Tagged" }
        assertEquals(listOf("Соло"), saved.tags)
    }

    @Test
    fun createNamed_withoutTag_leavesTagsEmpty() {
        val lifecycle = LifecycleRegistry()
        val component = DefaultChoreographyComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        component.onIntent(ChoreographyIntent.CreateNamed("No tag", "Bachata"))
        val editor = (component.stack.value.active.instance as ChoreographyComponent.Child.SequenceEditor).component
        editor.onIntent(SequenceEditorIntent.Save)

        val saved = component.uiState.value.items.first { it.title == "No tag" }
        assertTrue(saved.tags.isEmpty())
    }

    @Test
    fun timelineZoom_defaultsAndSnapsTo10PercentSteps() {
        val lifecycle = LifecycleRegistry()
        val editor = DefaultSequenceEditorStubComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        assertEquals(DEFAULT_TIMELINE_ZOOM, editor.uiState.value.timelineZoom)
        editor.onIntent(SequenceEditorIntent.SetTimelineZoom(0.0f))
        assertEquals(0.1f, editor.uiState.value.timelineZoom)
        editor.onIntent(SequenceEditorIntent.SetTimelineZoom(0.34f))
        assertEquals(0.3f, editor.uiState.value.timelineZoom)
        editor.onIntent(SequenceEditorIntent.SetTimelineZoom(5f))
        assertEquals(1.0f, editor.uiState.value.timelineZoom)
        // Zoom is a view setting: no undo entry.
        assertFalse(editor.uiState.value.canUndo)
    }

    @Test
    fun createNamed_upsertsDraftImmediately_andBackKeepsIt() {
        // C-213: create → leave without Save → vault still lists the empty draft.
        val lifecycle = LifecycleRegistry()
        val component = DefaultChoreographyComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        component.onIntent(ChoreographyIntent.CreateNamed("Draft Flow", "Bachata"))
        val draft = component.uiState.value.items.first { it.title == "Draft Flow" }
        assertEquals(VaultStatus.Draft, draft.status)
        assertTrue(draft.snapshot?.elementClips.isNullOrEmpty())
        assertTrue(component.stack.value.active.instance is ChoreographyComponent.Child.SequenceEditor)

        assertTrue(component.onBack())
        assertTrue(component.stack.value.active.instance is ChoreographyComponent.Child.Hub)
        assertTrue(component.uiState.value.items.any { it.id == draft.id && it.title == "Draft Flow" })
    }

    @Test
    fun splitSelected_cutsAtPlayhead_notHalf() {
        // C-215: playhead inside selected clip → split there; keep mediaUri on both.
        val lifecycle = LifecycleRegistry()
        val editor = DefaultSequenceEditorStubComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        editor.onIntent(
            SequenceEditorIntent.AddElementClips(
                listOf(EditorClip("A", 100_000L, mediaUri = "content://el/a")),
            ),
        )
        // Seek to 25% of 100s → 25_000ms local.
        editor.onIntent(SequenceEditorIntent.SeekTo(0.25f))
        editor.onIntent(SequenceEditorIntent.SelectElement(0))
        editor.onIntent(SequenceEditorIntent.SplitSelected)

        val clips = editor.uiState.value.elementClips
        assertEquals(2, clips.size)
        assertEquals(25_000L, clips[0].durationMs)
        assertEquals(75_000L, clips[1].durationMs)
        assertEquals("content://el/a", clips[0].mediaUri)
        assertEquals("content://el/a", clips[1].mediaUri)
        // C-224: both halves keep the same label (no " · b" suffix).
        assertEquals("A", clips[0].label)
        assertEquals("A", clips[1].label)
        assertEquals(1, editor.uiState.value.selectedElementIndex)
        // C-242: second half continues in source media (not from 0).
        assertEquals(0L, clips[0].sourceStartMs)
        assertEquals(25_000L, clips[1].sourceStartMs)
    }

    @Test
    fun splitSelected_keepsSameLabel_onBothHalves() {
        val lifecycle = LifecycleRegistry()
        val editor = DefaultSequenceEditorStubComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        editor.onIntent(
            SequenceEditorIntent.AddElementClips(
                listOf(EditorClip("Flame Step", 80_000L, mediaUri = "content://el/flame")),
            ),
        )
        editor.onIntent(SequenceEditorIntent.SeekTo(0.5f))
        editor.onIntent(SequenceEditorIntent.SelectElement(0))
        editor.onIntent(SequenceEditorIntent.SplitSelected)

        val clips = editor.uiState.value.elementClips
        assertEquals(2, clips.size)
        assertEquals("Flame Step", clips[0].label)
        assertEquals("Flame Step", clips[1].label)
        assertFalse(clips[1].label.contains("· b"))
    }

    @Test
    fun splitSelected_music_cutsAtPlayhead() {
        // C-224: with music selected (no element), split music at playhead via musicTimelineClips.
        val lifecycle = LifecycleRegistry()
        val editor = DefaultSequenceEditorStubComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        editor.onIntent(
            SequenceEditorIntent.AddMusicClips(
                listOf(EditorClip("Bed", 100_000L, mediaUri = "content://mu/bed")),
            ),
        )
        editor.onIntent(SequenceEditorIntent.SeekTo(0.25f))
        editor.onIntent(SequenceEditorIntent.SelectMusic(0))
        assertEquals(-1, editor.uiState.value.selectedElementIndex)
        assertEquals(0, editor.uiState.value.selectedMusicIndex)
        editor.onIntent(SequenceEditorIntent.SplitSelected)

        val clips = editor.uiState.value.musicClips
        assertEquals(2, clips.size)
        assertEquals(25_000L, clips[0].durationMs)
        assertEquals(75_000L, clips[1].durationMs)
        assertEquals("Bed", clips[0].label)
        assertEquals("Bed", clips[1].label)
        assertEquals("content://mu/bed", clips[0].mediaUri)
        assertEquals("content://mu/bed", clips[1].mediaUri)
        assertEquals(1, editor.uiState.value.selectedMusicIndex)
        assertEquals(0L, clips[0].sourceStartMs)
        assertEquals(25_000L, clips[1].sourceStartMs)
    }

    @Test
    fun splitSelected_music_respectsSongSpeed_timelineLocal() {
        // At 2× song speed, timeline is half; 50% playhead → 50% of source still.
        val lifecycle = LifecycleRegistry()
        val editor = DefaultSequenceEditorStubComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        editor.onIntent(
            SequenceEditorIntent.AddMusicClips(listOf(EditorClip("Fast", 100_000L))),
        )
        editor.onIntent(SequenceEditorIntent.SetSongPlaybackSpeed(2.0))
        // durationMs timeline = 50_000; seek 0.5 → playhead 25_000 timeline local
        // sourceCut = 25_000 * 2 = 50_000
        editor.onIntent(SequenceEditorIntent.SeekTo(0.5f))
        editor.onIntent(SequenceEditorIntent.SelectMusic(0))
        editor.onIntent(SequenceEditorIntent.SplitSelected)

        val clips = editor.uiState.value.musicClips
        assertEquals(2, clips.size)
        assertEquals(50_000L, clips[0].durationMs)
        assertEquals(50_000L, clips[1].durationMs)
        assertEquals("Fast", clips[0].label)
        assertEquals("Fast", clips[1].label)
    }

    @Test
    fun splitSelected_noopWhenPlayheadOutsideSelected() {
        val lifecycle = LifecycleRegistry()
        val editor = DefaultSequenceEditorStubComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        editor.onIntent(
            SequenceEditorIntent.AddElementClips(
                listOf(EditorClip("A", 40_000L), EditorClip("B", 60_000L)),
            ),
        )
        // Playhead in B (0.5 of 100s = 50s → inside B); select A → no-op.
        editor.onIntent(SequenceEditorIntent.SeekTo(0.5f))
        editor.onIntent(SequenceEditorIntent.SelectElement(0))
        editor.onIntent(SequenceEditorIntent.SplitSelected)
        assertEquals(2, editor.uiState.value.elementClips.size)
        assertEquals(40_000L, editor.uiState.value.elementClips[0].durationMs)
    }

    @Test
    fun elementClipSpeed_shrinksTimelineWidth() {
        // C-216: SetClipSpeed on selected element → effectiveMs = duration / speed.
        val lifecycle = LifecycleRegistry()
        val editor = DefaultSequenceEditorStubComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        editor.onIntent(
            SequenceEditorIntent.AddElementClips(listOf(EditorClip("A", 100_000L))),
        )
        assertEquals(100_000L, editor.uiState.value.durationMs)
        editor.onIntent(SequenceEditorIntent.SelectElement(0))
        editor.onIntent(SequenceEditorIntent.SetClipSpeed(2.0))

        val after = editor.uiState.value
        assertEquals(2.0, after.elementClips.single().speed)
        assertEquals(50_000L, after.elementsDurationMs)
        assertEquals(listOf(50_000L), after.elementTimelineClips.map { it.durationMs })
        assertEquals(50_000L, after.durationMs)
        // Source duration untouched.
        assertEquals(100_000L, after.elementClips.single().durationMs)
    }
}
