package com.midnightdancer.feature.music.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicLogicSmokeTest {

    @Test
    fun intent_refresh_keepsPlaceholderTracks() {
        val lifecycle = LifecycleRegistry()
        val component = DefaultMusicComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        assertEquals("Музыка", component.uiState.value.title)
        assertEquals(8, component.uiState.value.libraryCount)
        assertTrue(component.uiState.value.placeholderTracks.isNotEmpty())
        component.onIntent(MusicIntent.RefreshPlaceholders)
        assertEquals(8, component.uiState.value.placeholderTracks.size)
    }
}
