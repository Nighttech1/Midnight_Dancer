package com.midnightdancer.feature.elements.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ElementsLogicSmokeTest {

    @Test
    fun intent_refresh_keepsPlaceholderList() {
        val component = createComponent()

        assertEquals("Элементы", component.uiState.value.title)
        assertTrue(component.uiState.value.cards.size >= 24)
        val size = component.uiState.value.cards.size
        assertEquals(size, component.uiState.value.placeholderItems.size)
        component.onIntent(ElementsIntent.RefreshPlaceholders)
        assertTrue(component.uiState.value.cards.size >= 24)
        assertEquals(
            ElementCardKind.VideoCover,
            component.uiState.value.cards.first { it.id == "wave-to-self-swivels" }.kind,
        )
        assertEquals(
            ElementCardKind.Waveform,
            component.uiState.value.cards.first { it.id == "lateral-wave" }.kind,
        )
        assertEquals(
            ElementCardKind.CueOverlay,
            component.uiState.value.cards.first { it.id == "sensual-head-roll" }.kind,
        )
    }

    @Test
    fun selectFilter_and_search_filterLocally() {
        val component = createComponent()

        component.onIntent(ElementsIntent.SelectFilter(FILTER_BEGINNER))
        assertEquals("Начинающий", component.uiState.value.filterCategory)
        assertTrue(component.uiState.value.cards.size >= 2)
        assertTrue(component.uiState.value.cards.all { it.levelLabel == "Начинающий" })

        component.onIntent(ElementsIntent.SetSearchQuery("боковая"))
        assertEquals(1, component.uiState.value.cards.size)
        assertEquals("lateral-wave", component.uiState.value.cards.single().id)

        component.onIntent(ElementsIntent.RefreshPlaceholders)
        assertNull(component.uiState.value.filterCategory)
        assertEquals("", component.uiState.value.searchQuery)
        assertTrue(component.uiState.value.cards.size >= 12)
    }

    @Test
    fun play_isExclusive_and_detailStopsPlay() {
        val component = createComponent()
        component.onIntent(ElementsIntent.PlayCard("wave-to-self-swivels"))
        assertEquals("wave-to-self-swivels", component.uiState.value.playingCardId)
        component.onIntent(ElementsIntent.PlayCard("lateral-wave"))
        assertEquals("lateral-wave", component.uiState.value.playingCardId)
        component.onIntent(ElementsIntent.OpenDetail("sensual-head-roll"))
        assertNull(component.uiState.value.playingCardId)
        assertEquals("sensual-head-roll", component.uiState.value.detailCardId)
    }

    @Test
    fun sort_alphabetical() {
        val component = createComponent()
        component.onIntent(ElementsIntent.SetSortMode(ElementsSortMode.Alphabetical))
        val titles = component.uiState.value.cards.map { it.title }
        assertEquals(titles.sortedBy { it.lowercase() }, titles)
    }

    private fun createComponent(): DefaultElementsComponent {
        val lifecycle = LifecycleRegistry()
        val component = DefaultElementsComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()
        return component
    }
}
