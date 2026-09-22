package com.midnightdancer.core.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/**
 * Public root contract for Android Compose (C-06) and iOS SwiftUI (C-07).
 */
interface RootComponent {
    val stack: Value<ChildStack<*, Child>>
    val uiState: StateFlow<HelloUiState>
    fun onHelloClick()

    sealed class Child {
        class Dummy(val component: DummyComponent) : Child()
    }
}

interface DummyComponent

class DefaultDummyComponent(
    componentContext: ComponentContext,
) : DummyComponent, ComponentContext by componentContext

class DefaultRootComponent(
    componentContext: ComponentContext,
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    private val _uiState = MutableStateFlow(HelloUiState())
    override val uiState: StateFlow<HelloUiState> = _uiState.asStateFlow()

    override val stack: Value<ChildStack<*, RootComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Dummy,
        handleBackButton = true,
        childFactory = ::child,
    )

    override fun onHelloClick() {
        _uiState.update { state ->
            state.copy(clickCount = state.clickCount + 1)
        }
    }

    private fun child(config: Config, context: ComponentContext): RootComponent.Child =
        when (config) {
            Config.Dummy -> RootComponent.Child.Dummy(DefaultDummyComponent(context))
        }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Dummy : Config
    }
}
