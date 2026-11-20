package com.midnightdancer.feature.trainer.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.midnightdancer.core.domain.entity.*
import com.midnightdancer.core.domain.styles.*
import com.midnightdancer.core.domain.usecase.LoadLibrary
import com.midnightdancer.core.tts.RecordingTtsEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CanonicalTrainerStylesTest {
    @Test fun freshReaddedNameKeepsSelectedIdThroughRoomAndCatalogHydration() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val lifecycle = LifecycleRegistry()
        try {
            val states = MutableStateFlow<DanceStyleCatalogSnapshot?>(DanceStyleCatalogSnapshot(2,
                listOf(DanceStyleEntry(ReservedDanceStyle.ID, ReservedDanceStyle.NAME, true), DanceStyleEntry("fresh", "Alpha")),
                mapOf("old" to ReservedDanceStyle.ID), mapOf("alpha" to ReservedDanceStyle.ID)))
            val manager = object : ManageDanceStyles {
                override val state = states.asStateFlow()
                override suspend fun bootstrap(legacyNames: List<String>) = Unit
                override suspend fun add(name: String) = Unit
                override suspend fun rename(id: String, name: String) = Unit
                override suspend fun remove(id: String) = Unit
            }
            val data = MutableStateFlow(AppData(danceStyles = listOf(
                DanceStyle(ReservedDanceStyle.ID, ReservedDanceStyle.NAME, listOf(Move("wrong", "Wrong cue", ""))),
                DanceStyle("fresh", "Alpha", listOf(Move("right", "Correct cue", ""))),
            )))
            val load = object : LoadLibrary {
                override suspend fun load() = data.value
                override fun observe(): Flow<AppData> = data
            }
            val component = DefaultTrainerComponent(DefaultComponentContext(lifecycle), RecordingTtsEngine(), loadLibrary = load, danceStyles = manager)
            val uiHub = (component.stack.value.active.instance as TrainerComponent.Child.Hub).component
            uiHub.onIntent(TrainerIntent.SetStyle("Alpha"))
            assertEquals("fresh", component.uiState.value.selectedStyleId)
            states.value = states.value!!.copy(revision = 3)
            data.value = data.value.copy(songs = listOf(Song("s", "Song", "fresh", "", "owned.wav", 1.0, 1)))
            runCurrent()
            val hub = component.uiState.value
            assertEquals("fresh", hub.selectedStyleId)
            assertEquals("Alpha", hub.styleName)
            val session = TrainerSessionFactory.build(hub.copy(intervalSec = 2f), data.value)
            assertTrue(session.cueSchedule.isNotEmpty())
            assertTrue(session.cueSchedule.all { it.label == "Correct cue" })
            val missing = TrainerSessionFactory.build(hub.copy(selectedStyleId = "missing", intervalSec = 2f), data.value)
            assertFalse(missing.cueSchedule.any { it.label == "Correct cue" })
        } finally { lifecycle.destroy(); Dispatchers.resetMain() }
    }

    @Test fun actualHubRouteKeepsEmptyFreshStyleWhenUnrelatedStyleIsAddedOrRemoved() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val lifecycle = LifecycleRegistry()
        try {
            val reserved = DanceStyleEntry(ReservedDanceStyle.ID, ReservedDanceStyle.NAME, true)
            val fresh = DanceStyleEntry("fresh-empty", "Alpha")
            val original = DanceStyleCatalogSnapshot(2, listOf(reserved, fresh),
                mapOf("deleted-alpha" to ReservedDanceStyle.ID), mapOf("alpha" to ReservedDanceStyle.ID))
            val states = MutableStateFlow<DanceStyleCatalogSnapshot?>(original)
            val manager = object : ManageDanceStyles {
                override val state = states.asStateFlow()
                override suspend fun bootstrap(legacyNames: List<String>) = Unit
                override suspend fun add(name: String) = Unit
                override suspend fun rename(id: String, name: String) = Unit
                override suspend fun remove(id: String) = Unit
            }
            val emptyLibrary = AppData(danceStyles = listOf(
                DanceStyle(reserved.id, reserved.name), DanceStyle(fresh.id, fresh.name)))
            val data = MutableStateFlow(emptyLibrary)
            val load = object : LoadLibrary {
                override suspend fun load() = data.value
                override fun observe(): Flow<AppData> = data
            }
            val component = DefaultTrainerComponent(DefaultComponentContext(lifecycle), RecordingTtsEngine(), loadLibrary = load, danceStyles = manager)
            val hub = (component.stack.value.active.instance as TrainerComponent.Child.Hub).component
            hub.onIntent(TrainerIntent.SetStyle("Alpha")) // Actual UI route, not the parent test shortcut.
            fun assertFresh() {
                assertEquals("fresh-empty", hub.uiState.value.selectedStyleId)
                assertEquals("Alpha", hub.uiState.value.styleName)
                assertEquals(component.uiState.value, hub.uiState.value)
            }
            assertFresh()
            val unrelated = DanceStyleEntry("unrelated", "Other QA")
            // Manager publishes first while Room still contains the previous, empty selected style.
            states.value = original.copy(revision = 3, entries = original.entries + unrelated)
            runCurrent(); assertFresh()
            data.value = emptyLibrary.copy(danceStyles = emptyLibrary.danceStyles + DanceStyle(unrelated.id, unrelated.name))
            runCurrent(); assertFresh()
            // Exercise the opposite delivery order on unrelated deletion.
            data.value = emptyLibrary
            runCurrent(); assertFresh()
            states.value = original.copy(revision = 4)
            runCurrent(); assertFresh()
            // Deleting the selected ID itself must still intentionally move selection to reserved.
            states.value = original.copy(revision = 5, entries = listOf(reserved),
                redirects = original.redirects + (fresh.id to ReservedDanceStyle.ID))
            runCurrent()
            assertEquals(ReservedDanceStyle.ID, hub.uiState.value.selectedStyleId)
            assertEquals(ReservedDanceStyle.NAME, hub.uiState.value.styleName)
        } finally { lifecycle.destroy(); Dispatchers.resetMain() }
    }
}
