package com.midnightdancer.shared

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.midnightdancer.core.domain.DomainMarker
import com.midnightdancer.core.navigation.RootComponent
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedSmokeTest {

    @BeforeTest
    fun setUp() {
        initKoin()
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun shared_exportsDomainMarker() {
        assertEquals("core-domain", DomainMarker.NAME)
        assertEquals("Shared", SharedApi.FRAMEWORK_NAME)
    }

    @Test
    fun koin_rootFactory_helloClickChangesState() {
        val lifecycle = LifecycleRegistry()
        val context = DefaultComponentContext(lifecycle = lifecycle)
        val root: RootComponent =
            org.koin.core.context.GlobalContext.get().get(parameters = { parametersOf(context) })

        assertEquals(0, root.uiState.value.clickCount)
        root.onHelloClick()
        assertEquals(1, root.uiState.value.clickCount)
        assertTrue(root.stack.value.active.instance is RootComponent.Child.Dummy)
    }
}
