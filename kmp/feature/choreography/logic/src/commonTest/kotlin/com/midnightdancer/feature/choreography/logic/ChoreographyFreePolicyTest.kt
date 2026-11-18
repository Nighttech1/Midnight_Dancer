package com.midnightdancer.feature.choreography.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.midnightdancer.core.domain.settings.CreationAccessPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlin.test.*

class ChoreographyFreePolicyTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    @Test fun demoDoesNotConsumeFreeSlotAndCopyCannotBypassGate() {
        assertEquals(0, ownChoreographyCount(DEFAULT_VAULT_ITEMS))
        val snapshot = ChoreographySnapshot("Own", "Salsa")
        val (state, id) = upsertSnapshot(ChoreographyUiState(), null, snapshot)
        assertEquals(1, ownChoreographyCount(state.items))
        val blocked = reduceHubState(state, ChoreographyIntent.CopyItem(id), canCreate = false)
        assertEquals(state.items, blocked.items)
        assertNotNull(blocked.creationBlockedMessage)
        assertEquals(state.items.size + 1, reduceHubState(state, ChoreographyIntent.CopyItem(id), true).items.size)
    }

    @Test fun persistedEmptyVaultStaysEmptyAndFirstFreeCreationWorks() {
        val persister = object : ChoreographyVaultPersister {
            var raw = encodeVaultItems(emptyList())
            override fun readRaw(): String = raw
            override fun writeRaw(json: String) { raw = json }
        }
        val component = DefaultChoreographyComponent(DefaultComponentContext(LifecycleRegistry()),
            vaultPersister = persister, creationAccess = CreationAccessPolicy { it < 1 })
        assertTrue(component.uiState.value.items.isEmpty())
        component.openNewSequenceEditor("First", "Salsa")
        component.onBack()
        assertEquals(1, component.uiState.value.items.size)
        component.openNewSequenceEditor("Second", "Salsa")
        assertEquals(1, component.uiState.value.items.size)
        assertNotNull(component.uiState.value.creationBlockedMessage)
        component.openSequenceEditor(component.uiState.value.items.first().id)
        assertTrue(component.stack.value.active.instance is ChoreographyComponent.Child.SequenceEditor)
    }

    @Test fun proCopyCommitsExactlyOnceWithObservableVault() {
        val (initial, sourceId) = upsertSnapshot(ChoreographyUiState(items = emptyList()), null,
            ChoreographySnapshot("Source", "Salsa"))
        val persister = object : ChoreographyVaultPersister {
            var raw = encodeVaultItems(initial.items)
            override fun readRaw(): String = raw
            override fun writeRaw(json: String) { raw = json }
        }
        val component = DefaultChoreographyComponent(DefaultComponentContext(LifecycleRegistry()),
            vaultPersister = persister, creationAccess = CreationAccessPolicy { true })
        val hub = (component.stack.value.active.instance as ChoreographyComponent.Child.Hub).component
        hub.onIntent(ChoreographyIntent.CopyItem(sourceId))
        assertEquals(2, component.uiState.value.items.size)
        assertEquals(2, decodeVaultItems(persister.raw).size)
        assertEquals(2, ChoreographyVaultStore.all().size)
    }
}
