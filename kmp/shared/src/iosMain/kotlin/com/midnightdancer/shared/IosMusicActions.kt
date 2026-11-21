package com.midnightdancer.shared

import com.midnightdancer.feature.music.logic.MusicComponent
import com.midnightdancer.feature.music.logic.MusicIntent

/** Swift-friendly commands; the shared component remains the only catalog writer. */
object IosMusicActions {
    fun addPicked(component: MusicComponent, title: String, reference: String, durationMs: Long) =
        component.onIntent(MusicIntent.AddPickedTrack(title, reference, durationMs))

    fun save(component: MusicComponent, id: String, title: String, style: String,
             tags: List<String>, speed: Double) =
        component.onIntent(MusicIntent.SaveTrackEdit(id, title, style, tags, speed))

    fun seek(component: MusicComponent, id: String, progress: Float) =
        component.onIntent(MusicIntent.SeekTrack(id, progress))
}
