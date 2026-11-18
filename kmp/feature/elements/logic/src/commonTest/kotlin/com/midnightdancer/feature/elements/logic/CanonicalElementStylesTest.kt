package com.midnightdancer.feature.elements.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.midnightdancer.core.domain.entity.*
import com.midnightdancer.core.domain.styles.*
import com.midnightdancer.core.domain.usecase.LoadLibrary
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class CanonicalElementStylesTest {
    @Test fun deletedSelectedStyleAndOpenDraftMoveToReservedWithoutArbitraryRedistribution() = runBlocking {
        val reserved = DanceStyleEntry(ReservedDanceStyle.ID, ReservedDanceStyle.NAME, true)
        val styles = MutableStateFlow<DanceStyleCatalogSnapshot?>(DanceStyleCatalogSnapshot(1,
            listOf(DanceStyleEntry("a", "Alpha"), reserved, DanceStyleEntry("b", "Beta"))))
        val manager = object : ManageDanceStyles {
            override val state = styles.asStateFlow()
            override suspend fun bootstrap(legacyNames: List<String>) = Unit
            override suspend fun add(name: String) = Unit
            override suspend fun rename(id: String, name: String) = Unit
            override suspend fun remove(id: String) = Unit
        }
        val data = MutableStateFlow(AppData(danceStyles = listOf(DanceStyle("a", "Alpha",
            listOf(Move("m", "Own", "", videoUri = "owned.mp4"))))))
        val load = object : LoadLibrary {
            override suspend fun load() = data.value
            override fun observe(): Flow<AppData> = data
        }
        val lifecycle = LifecycleRegistry()
        val component = DefaultElementsComponent(DefaultComponentContext(lifecycle), loadLibrary = load, danceStyles = manager)
        try {
            withTimeout(3000) { component.uiState.first { it.catalogCards.any { row -> row.id == "m" } && it.styles.first().id == "a" } }
            if (component.uiState.value.selectedStyle.id != "a") component.stylePrevious()
            assertEquals("a", component.uiState.value.selectedStyle.id)
            component.onIntent(ElementsIntent.OpenEditor("m"))
            styles.value = DanceStyleCatalogSnapshot(2, listOf(reserved, DanceStyleEntry("b", "Beta")), mapOf("a" to ReservedDanceStyle.ID))
            val updated = withTimeout(3000) { component.uiState.first {
                it.selectedStyle.id == ReservedDanceStyle.ID && it.editor?.styleId == ReservedDanceStyle.ID &&
                    it.catalogCards.single().styleId == ReservedDanceStyle.ID
            } }
            assertEquals("owned.mp4", updated.catalogCards.single().videoUri)
            assertEquals(listOf(ReservedDanceStyle.NAME, "Beta"), updated.styles.map { it.name })
        } finally { lifecycle.destroy() }
    }
}
