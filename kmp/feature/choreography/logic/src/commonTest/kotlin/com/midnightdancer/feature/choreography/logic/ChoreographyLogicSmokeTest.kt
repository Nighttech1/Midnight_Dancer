package com.midnightdancer.feature.choreography.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChoreographyLogicSmokeTest {

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
}
