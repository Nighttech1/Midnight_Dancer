package com.midnightdancer.shared

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.midnightdancer.core.domain.DomainMarker
import com.midnightdancer.core.navigation.MainComponent
import com.midnightdancer.core.navigation.NavTab
import com.midnightdancer.core.navigation.RootComponent
import com.midnightdancer.feature.splash.logic.SplashIntent
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SharedSmokeTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        initKoin()
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun shared_exportsDomainMarker() {
        assertEquals("core-domain", DomainMarker.NAME)
        assertEquals("Shared", SharedApi.FRAMEWORK_NAME)
    }

    @Test
    fun koin_rootFactory_exposesMainTabsAndSettings() {
        val lifecycle = LifecycleRegistry()
        val context = DefaultComponentContext(lifecycle = lifecycle)
        val root: RootComponent =
            org.koin.core.context.GlobalContext.get().get(parameters = { parametersOf(context) })
        lifecycle.resume()
        val splash = root.stack.value.active.instance
        if (splash is RootComponent.Child.Splash) {
            splash.component.onIntent(SplashIntent.Continue)
        }

        assertTrue(root.stack.value.active.instance is RootComponent.Child.Main)
        val main = root.main
        assertNotNull(main)
        assertEquals(5, main.pages.value.items.size)
        assertEquals(NavTab.ELEMENTS, main.selectedTab)

        main.onTabSelected(NavTab.MUSIC)
        assertEquals(NavTab.MUSIC, main.selectedTab)

        main.openSettings()
        assertTrue(main.overlayStack.value.active.instance is MainComponent.OverlayChild.Settings)
        assertTrue(main.onBack())
        assertTrue(main.overlayStack.value.active.instance is MainComponent.OverlayChild.None)
    }
}
