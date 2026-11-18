package com.midnightdancer.feature.settings.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.midnightdancer.core.domain.styles.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class CanonicalSettingsStylesTest {
    @Test fun settingsObservesCommittedCanonicalRowsAndShowsMutationFailure() = runBlocking {
        val reserved = DanceStyleEntry(ReservedDanceStyle.ID, ReservedDanceStyle.NAME, true)
        val states = MutableStateFlow<DanceStyleCatalogSnapshot?>(null)
        val manager = object : ManageDanceStyles {
            override val state = states.asStateFlow()
            override suspend fun bootstrap(legacyNames: List<String>) = Unit
            override suspend fun add(name: String) { error("Не удалось записать стиль") }
            override suspend fun rename(id: String, name: String) = Unit
            override suspend fun remove(id: String) { error("Стиль «Без стиля» нельзя удалить.") }
        }
        val lifecycle = LifecycleRegistry()
        val component = DefaultSettingsComponent(DefaultComponentContext(lifecycle), danceStyles = manager)
        try {
            assertTrue(component.uiState.value.stylesLoading)
            states.value = DanceStyleCatalogSnapshot(1, listOf(reserved, DanceStyleEntry("a", "Alpha")))
            withTimeout(3000) { component.uiState.first { it.styleEntries.size == 2 } }
            component.onIntent(SettingsIntent.RemoveStyle(ReservedDanceStyle.NAME))
            val failed = withTimeout(3000) { component.uiState.first { !it.stylesBusy && it.stylesError != null } }
            assertEquals(listOf(ReservedDanceStyle.NAME, "Alpha"), failed.danceStyles)
            assertEquals(ReservedDanceStyle.ID, failed.styleEntries.first().id)
            states.value = DanceStyleCatalogSnapshot(2, listOf(reserved))
            withTimeout(3000) { component.uiState.first { it.danceStyles == listOf(ReservedDanceStyle.NAME) } }
        } finally { lifecycle.destroy() }
        Unit
    }
}
