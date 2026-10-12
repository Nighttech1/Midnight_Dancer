package com.midnightdancer.core.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.midnightdancer.feature.choreography.logic.ChoreographyComponent
import com.midnightdancer.feature.splash.logic.SplashIntent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * ChildPages may create Trainer early; DefaultTrainerComponent uses Dispatchers.Main.immediate.
 * Unit hosts need setMain (parity with TrainerLogicSmokeTest) or Main is absent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RootComponentSmokeTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun root_splashAutoAdvancesToMain() {
        val root = createRoot()
        val active = root.stack.value.active.instance
        assertTrue(active is RootComponent.Child.Main, "expected Main after splash Continue")
        assertNotNull(root.main)
    }

    @Test
    fun main_hasFiveTabsInFlutterOrder() {
        val main = requireMain()
        val pages = main.pages.value
        assertEquals(5, pages.items.size)
        assertEquals(0, pages.selectedIndex)
        assertEquals(NavTab.ELEMENTS, main.selectedTab)

        // Child Pages may DESTROY far tabs — select each and verify shell title order.
        NavTab.ordered.forEachIndexed { index, tab ->
            main.onTabSelected(index)
            assertEquals(index, main.pages.value.selectedIndex)
            assertEquals(tab, main.selectedTab)
            val active = main.pages.value.items[index].instance
            assertNotNull(active)
            val title = when (active) {
                is MainComponent.TabChild.Elements -> active.component.uiState.value.title
                is MainComponent.TabChild.Music -> active.component.uiState.value.title
                is MainComponent.TabChild.Choreography -> active.component.uiState.value.title
                is MainComponent.TabChild.Trainer -> active.component.uiState.value.title
                is MainComponent.TabChild.Exchange -> active.component.uiState.value.title
            }
            assertEquals(tab.title, title)
        }
    }

    @Test
    fun onTabSelected_switchesSelectedIndex() {
        val main = requireMain()
        assertEquals(NavTab.ELEMENTS, main.selectedTab)

        main.onTabSelected(NavTab.TRAINER)
        assertEquals(3, main.pages.value.selectedIndex)
        assertEquals(NavTab.TRAINER, main.selectedTab)

        main.onTabSelected(4)
        assertEquals(NavTab.EXCHANGE, main.selectedTab)
    }

    @Test
    fun openSettings_pushAndBack() {
        val main = requireMain()
        assertTrue(main.overlayStack.value.active.instance is MainComponent.OverlayChild.None)

        main.openSettings()
        val settings = main.overlayStack.value.active.instance
        assertTrue(settings is MainComponent.OverlayChild.Settings)
        assertEquals("Settings", settings.component.uiState.value.title)

        assertTrue(main.onBack())
        assertTrue(main.overlayStack.value.active.instance is MainComponent.OverlayChild.None)
        assertFalse(main.onBack())
    }

    @Test
    fun root_onBack_popsSettings() {
        val root = createRoot()
        val main = root.main
        assertNotNull(main)

        main.openSettings()
        assertTrue(root.onBack())
        assertTrue(main.overlayStack.value.active.instance is MainComponent.OverlayChild.None)
    }

    @Test
    fun choreography_sequenceEditorStub_pushBack() {
        val main = requireMain()
        main.onTabSelected(NavTab.CHOREOGRAPHY)
        val choreo = main.pages.value.items[2].instance as MainComponent.TabChild.Choreography

        assertTrue(choreo.component.stack.value.active.instance is ChoreographyComponent.Child.Hub)
        choreo.component.openSequenceEditor()
        assertTrue(
            choreo.component.stack.value.active.instance is ChoreographyComponent.Child.SequenceEditor,
        )
        assertTrue(choreo.component.onBack())
        assertTrue(choreo.component.stack.value.active.instance is ChoreographyComponent.Child.Hub)
    }

    private fun requireMain(): MainComponent {
        val root = createRoot()
        val main = root.main
        assertNotNull(main)
        return main
    }

    private fun createRoot(): RootComponent {
        val lifecycle = LifecycleRegistry()
        val root = DefaultRootComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()
        // Production Root passes autoFinish=false; TTS prepare (Recording fallback) then Continue.
        val splash = root.stack.value.active.instance
        if (splash is RootComponent.Child.Splash) {
            assertTrue(splash.component.uiState.value.bootReady)
            splash.component.onIntent(SplashIntent.Continue)
        }
        return root
    }
}
