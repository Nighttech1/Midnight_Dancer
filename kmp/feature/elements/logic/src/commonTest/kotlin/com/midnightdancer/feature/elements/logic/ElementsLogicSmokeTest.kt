package com.midnightdancer.feature.elements.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ElementsLogicSmokeTest {

    @Test
    fun intent_refresh_keepsEmptyCatalog() {
        val component = createComponent()

        assertEquals("Элементы", component.uiState.value.title)
        assertEquals(0, component.uiState.value.catalogCards.size)
        assertTrue(component.uiState.value.cards.isEmpty())
        component.onIntent(ElementsIntent.RefreshPlaceholders)
        assertTrue(component.uiState.value.cards.isEmpty())
    }

    @Test
    fun styleChange_withEmptyCatalog_staysEmpty() {
        val component = createComponent()
        val styles = component.uiState.value.styles
        assertTrue(styles.size >= 3)
        styles.forEach { _ ->
            component.onIntent(ElementsIntent.StyleNext)
        }
        assertTrue(component.uiState.value.cards.isEmpty())
    }

    @Test
    fun selectFilter_and_search_onEmptyCatalog() {
        val component = createComponent()

        component.onIntent(ElementsIntent.SelectFilter(FILTER_BEGINNER))
        assertEquals("Начинающий", component.uiState.value.filterCategory)
        assertTrue(component.uiState.value.cards.isEmpty())

        component.onIntent(ElementsIntent.SetSearchQuery("боковая"))
        assertTrue(component.uiState.value.cards.isEmpty())

        component.onIntent(ElementsIntent.RefreshPlaceholders)
        assertNull(component.uiState.value.filterCategory)
        assertEquals("", component.uiState.value.searchQuery)
    }

    @Test
    fun playCard_and_openDetail_openSameEditor_afterSave() {
        val component = createComponent()
        component.onIntent(ElementsIntent.AddNewElement)
        component.onIntent(
            ElementsIntent.UpdateEditorDraft(
                assertNotNull(component.uiState.value.editor).copy(title = "Тест"),
            ),
        )
        component.onIntent(ElementsIntent.SaveElement)
        val id = component.uiState.value.catalogCards.single().id

        component.onIntent(ElementsIntent.PlayCard(id))
        val first = assertNotNull(component.uiState.value.editor)
        assertEquals(id, first.cardId)

        component.onIntent(ElementsIntent.CloseEditor)
        component.onIntent(ElementsIntent.OpenDetail(id))
        assertEquals(id, component.uiState.value.editor?.cardId)
    }

    @Test
    fun addElement_opensEditorDraft_andSaveInsertsWithOptionalTag() {
        val component = createComponent()
        val before = component.uiState.value.catalogCards.size

        component.onIntent(ElementsIntent.AddNewElement)
        val draft = assertNotNull(component.uiState.value.editor)
        assertNull(draft.cardId)
        assertNull(draft.tag)
        assertEquals(before, component.uiState.value.catalogCards.size)

        component.onIntent(ElementsIntent.UpdateEditorDraft(draft.copy(title = "Мой элемент")))
        component.onIntent(ElementsIntent.SaveElement)

        assertNull(component.uiState.value.editor)
        assertEquals(before + 1, component.uiState.value.catalogCards.size)
        val saved = component.uiState.value.catalogCards.first { it.title == "Мой элемент" }
        assertTrue(saved.tags.isEmpty())
        assertEquals(draft.styleId, saved.styleId)
        assertTrue(component.uiState.value.cards.any { it.id == saved.id })
    }

    @Test
    fun editStyle_movesCardToTargetStyleList() {
        val component = createComponent()
        component.onIntent(ElementsIntent.AddNewElement)
        val draft0 = assertNotNull(component.uiState.value.editor)
        component.onIntent(ElementsIntent.UpdateEditorDraft(draft0.copy(title = "Move me")))
        component.onIntent(ElementsIntent.SaveElement)

        val current = component.uiState.value.selectedStyle.id
        val card = component.uiState.value.cards.single()
        val target = component.uiState.value.styles.first { it.id != current }.id

        component.onIntent(ElementsIntent.OpenEditor(card.id))
        val draft = assertNotNull(component.uiState.value.editor)
        component.onIntent(ElementsIntent.UpdateEditorDraft(draft.copy(styleId = target)))
        component.onIntent(ElementsIntent.SaveElement)

        assertFalse(component.uiState.value.cards.any { it.id == card.id })
        while (component.uiState.value.selectedStyle.id != target) {
            component.onIntent(ElementsIntent.StyleNext)
        }
        assertTrue(component.uiState.value.cards.any { it.id == card.id })
    }

    @Test
    fun togglePin_putsCardFirst() {
        val component = createComponent()
        repeat(2) { i ->
            component.onIntent(ElementsIntent.AddNewElement)
            val d = assertNotNull(component.uiState.value.editor)
            component.onIntent(ElementsIntent.UpdateEditorDraft(d.copy(title = "E$i")))
            component.onIntent(ElementsIntent.SaveElement)
        }
        val last = component.uiState.value.cards.last()
        component.onIntent(ElementsIntent.TogglePin(last.id))
        assertEquals(last.id, component.uiState.value.cards.first().id)
    }

    @Test
    fun sort_alphabetical() {
        val component = createComponent()
        repeat(2) { i ->
            component.onIntent(ElementsIntent.AddNewElement)
            val d = assertNotNull(component.uiState.value.editor)
            component.onIntent(ElementsIntent.UpdateEditorDraft(d.copy(title = if (i == 0) "B" else "A")))
            component.onIntent(ElementsIntent.SaveElement)
        }
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
