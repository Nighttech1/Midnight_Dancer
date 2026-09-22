package com.midnightdancer.core.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RootComponentSmokeTest {

    @Test
    fun root_createsDummyChild() {
        val root = createRoot()
        val active = root.stack.value.active.instance
        assertTrue(active is RootComponent.Child.Dummy)
    }

    @Test
    fun onHelloClick_incrementsClickCount() {
        val root = createRoot()
        assertEquals(0, root.uiState.value.clickCount)

        root.onHelloClick()
        root.onHelloClick()

        assertEquals(2, root.uiState.value.clickCount)
        assertTrue(root.uiState.value.displayLabel.contains("2"))
    }

    private fun createRoot(): RootComponent {
        val lifecycle = LifecycleRegistry()
        return DefaultRootComponent(DefaultComponentContext(lifecycle = lifecycle))
    }
}
