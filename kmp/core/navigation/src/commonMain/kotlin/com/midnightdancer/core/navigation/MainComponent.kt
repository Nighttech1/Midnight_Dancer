package com.midnightdancer.core.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.pages.ChildPages
import com.arkivanov.decompose.router.pages.Pages
import com.arkivanov.decompose.router.pages.PagesNavigation
import com.arkivanov.decompose.router.pages.childPages
import com.arkivanov.decompose.router.pages.select
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.midnightdancer.feature.choreography.logic.ChoreographyComponent
import com.midnightdancer.feature.elements.logic.ElementsComponent
import com.midnightdancer.feature.exchange.logic.ExchangeComponent
import com.midnightdancer.feature.music.logic.MusicComponent
import com.midnightdancer.feature.settings.logic.SettingsComponent
import com.midnightdancer.feature.trainer.logic.TrainerComponent
import kotlinx.serialization.Serializable

/**
 * Main chrome: five Flutter-ordered tabs (Child Pages) + Settings Child Stack push.
 * Dialog Slot stubs reserved for LegalConsent / BackupOptions (empty until product).
 *
 * Tab / Settings children come from `:feature:*:logic` via [NavigationChildFactories].
 */
interface MainComponent {
    val pages: Value<ChildPages<*, TabChild>>
    val overlayStack: Value<ChildStack<*, OverlayChild>>
    val dialogSlot: Value<ChildSlot<*, DialogChild>>

    val selectedTab: NavTab

    fun onTabSelected(tab: NavTab)
    fun onTabSelected(index: Int)
    fun openSettings()

    /**
     * Pops Settings overlay (or nested tab stacks later). Returns true if consumed.
     */
    fun onBack(): Boolean

    sealed class TabChild {
        class Elements(val component: ElementsComponent) : TabChild()
        class Music(val component: MusicComponent) : TabChild()
        class Choreography(val component: ChoreographyComponent) : TabChild()
        class Trainer(val component: TrainerComponent) : TabChild()
        class Exchange(val component: ExchangeComponent) : TabChild()
    }

    sealed class OverlayChild {
        /** Tabs-only chrome (no pushed screen). */
        data object None : OverlayChild()
        class Settings(val component: SettingsComponent) : OverlayChild()
    }

    /** Stub slot for future LegalConsent / BackupOptions dialogs. */
    sealed class DialogChild {
        class LegalConsent : DialogChild()
        class BackupOptions : DialogChild()
    }
}

class DefaultMainComponent(
    componentContext: ComponentContext,
    private val factories: NavigationChildFactories = NavigationChildFactories(),
) : MainComponent, ComponentContext by componentContext {

    private val pagesNavigation = PagesNavigation<TabConfig>()
    private val overlayNavigation = StackNavigation<OverlayConfig>()
    private val dialogNavigation = SlotNavigation<DialogConfig>()

    override val pages: Value<ChildPages<*, MainComponent.TabChild>> = childPages(
        source = pagesNavigation,
        serializer = TabConfig.serializer(),
        initialPages = {
            Pages(
                items = TabConfig.ordered,
                selectedIndex = 0,
            )
        },
        childFactory = ::tabChild,
    )

    override val overlayStack: Value<ChildStack<*, MainComponent.OverlayChild>> = childStack(
        source = overlayNavigation,
        serializer = OverlayConfig.serializer(),
        initialConfiguration = OverlayConfig.None,
        handleBackButton = true,
        childFactory = ::overlayChild,
    )

    override val dialogSlot: Value<ChildSlot<*, MainComponent.DialogChild>> = childSlot(
        source = dialogNavigation,
        serializer = DialogConfig.serializer(),
        handleBackButton = true,
        childFactory = ::dialogChild,
    )

    override val selectedTab: NavTab
        get() = NavTab.fromIndex(pages.value.selectedIndex)

    override fun onTabSelected(tab: NavTab) {
        onTabSelected(tab.ordinal)
    }

    override fun onTabSelected(index: Int) {
        require(index in NavTab.ordered.indices) { "Tab index out of range: $index" }
        pagesNavigation.select(index = index)
    }

    override fun openSettings() {
        if (overlayStack.value.active.instance !is MainComponent.OverlayChild.Settings) {
            overlayNavigation.pushNew(OverlayConfig.Settings)
        }
    }

    override fun onBack(): Boolean {
        if (overlayStack.value.active.instance is MainComponent.OverlayChild.Settings) {
            var handled = false
            overlayNavigation.pop { didPop -> handled = didPop }
            return handled
        }

        val tabInstance = pages.value.items.getOrNull(pages.value.selectedIndex)?.instance
        return when (tabInstance) {
            is MainComponent.TabChild.Choreography -> tabInstance.component.onBack()
            is MainComponent.TabChild.Trainer -> tabInstance.component.onBack()
            else -> false
        }
    }

    private fun tabChild(
        config: TabConfig,
        context: ComponentContext,
    ): MainComponent.TabChild =
        when (config) {
            TabConfig.Elements ->
                MainComponent.TabChild.Elements(factories.elements(context))
            TabConfig.Music ->
                MainComponent.TabChild.Music(factories.music(context))
            TabConfig.Choreography ->
                MainComponent.TabChild.Choreography(factories.choreography(context))
            TabConfig.Trainer ->
                MainComponent.TabChild.Trainer(factories.trainer(context))
            TabConfig.Exchange ->
                MainComponent.TabChild.Exchange(factories.exchange(context))
        }

    private fun overlayChild(
        config: OverlayConfig,
        context: ComponentContext,
    ): MainComponent.OverlayChild =
        when (config) {
            OverlayConfig.None -> MainComponent.OverlayChild.None
            OverlayConfig.Settings ->
                MainComponent.OverlayChild.Settings(factories.settings(context))
        }

    private fun dialogChild(
        config: DialogConfig,
        context: ComponentContext,
    ): MainComponent.DialogChild =
        when (config) {
            DialogConfig.LegalConsent -> MainComponent.DialogChild.LegalConsent()
            DialogConfig.BackupOptions -> MainComponent.DialogChild.BackupOptions()
        }

    @Serializable
    private sealed interface TabConfig {
        @Serializable
        data object Elements : TabConfig

        @Serializable
        data object Music : TabConfig

        @Serializable
        data object Choreography : TabConfig

        @Serializable
        data object Trainer : TabConfig

        @Serializable
        data object Exchange : TabConfig

        companion object {
            val ordered: List<TabConfig> = listOf(
                Elements,
                Music,
                Choreography,
                Trainer,
                Exchange,
            )
        }
    }

    @Serializable
    private sealed interface OverlayConfig {
        @Serializable
        data object None : OverlayConfig

        @Serializable
        data object Settings : OverlayConfig
    }

    @Serializable
    private sealed interface DialogConfig {
        @Serializable
        data object LegalConsent : DialogConfig

        @Serializable
        data object BackupOptions : DialogConfig
    }
}
