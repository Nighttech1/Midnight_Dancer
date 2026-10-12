package com.midnightdancer.core.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.midnightdancer.feature.splash.logic.SplashComponent
import kotlinx.serialization.Serializable

/**
 * Public root contract for Android Compose and iOS SwiftUI.
 *
 * Tree: SplashStub → Main (tabs + Settings overlay). Feature shells live in
 * `:feature:*:logic`; this module owns the tree and [NavigationChildFactories].
 */
interface RootComponent {
    val stack: Value<ChildStack<*, Child>>

    /** Convenience when active child is Main; null on Splash. */
    val main: MainComponent?

    fun onBack(): Boolean

    sealed class Child {
        class Splash(val component: SplashComponent) : Child()
        class Main(val component: MainComponent) : Child()
    }
}

class DefaultRootComponent(
    componentContext: ComponentContext,
    private val factories: NavigationChildFactories = NavigationChildFactories(),
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    override val stack: Value<ChildStack<*, RootComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Splash,
        handleBackButton = true,
        childFactory = ::child,
    )

    override val main: MainComponent?
        get() = (stack.value.active.instance as? RootComponent.Child.Main)?.component

    override fun onBack(): Boolean {
        val active = stack.value.active.instance
        if (active is RootComponent.Child.Main) {
            if (active.component.onBack()) return true
        }
        var handled = false
        navigation.pop { didPop -> handled = didPop }
        return handled
    }

    private fun child(config: Config, context: ComponentContext): RootComponent.Child =
        when (config) {
            Config.Splash ->
                RootComponent.Child.Splash(
                    // Boot video stays until tap (SplashIntent.Continue); 3rd arg = autoFinish.
                    factories.splash(
                        context,
                        { navigation.replaceAll(Config.Main) },
                        false,
                    ),
                )
            Config.Main ->
                RootComponent.Child.Main(DefaultMainComponent(context, factories))
        }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Splash : Config

        @Serializable
        data object Main : Config
    }
}
