package com.midnightdancer.shared

import com.midnightdancer.feature.choreography.logic.ChoreoCatalog
import com.midnightdancer.feature.choreography.logic.ChoreoCatalogEntry
import com.midnightdancer.feature.choreography.logic.SequenceEditorIntent
import com.midnightdancer.feature.choreography.logic.SequenceEditorStubComponent

/** Named actions for the Swift bridge, preserving the same reducer as the Android editor. */
object IosChoreographyActions {
    fun selectElement(editor: SequenceEditorStubComponent, index: Int) =
        editor.onIntent(SequenceEditorIntent.SelectElement(index))

    fun selectMusic(editor: SequenceEditorStubComponent, index: Int) =
        editor.onIntent(SequenceEditorIntent.SelectMusic(index))

    fun split(editor: SequenceEditorStubComponent) = editor.onIntent(SequenceEditorIntent.SplitSelected)
    fun delete(editor: SequenceEditorStubComponent) = editor.onIntent(SequenceEditorIntent.DeleteSelected)
    fun undo(editor: SequenceEditorStubComponent) = editor.onIntent(SequenceEditorIntent.Undo)
    fun redo(editor: SequenceEditorStubComponent) = editor.onIntent(SequenceEditorIntent.Redo)

    fun reorderElements(editor: SequenceEditorStubComponent, from: Int, to: Int) =
        editor.onIntent(SequenceEditorIntent.ReorderElements(from, to))

    fun reorderMusic(editor: SequenceEditorStubComponent, from: Int, to: Int) =
        editor.onIntent(SequenceEditorIntent.ReorderMusic(from, to))

    fun musicChoices(editor: SequenceEditorStubComponent): List<ChoreoCatalogEntry> =
        ChoreoCatalog.tracksForStyle(editor.uiState.value.style).filter { !it.mediaUri.isNullOrBlank() }

    fun addMusic(editor: SequenceEditorStubComponent, id: String) {
        val entry = musicChoices(editor).firstOrNull { it.id == id } ?: return
        editor.onIntent(SequenceEditorIntent.AddMusicClips(listOf(ChoreoCatalog.trackClip(entry))))
    }
}
