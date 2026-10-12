package com.midnightdancer.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.midnightdancer.app.ui.splash.SplashScreen
import com.midnightdancer.core.navigation.RootComponent

/**
 * Binds Compose to shared Decompose [RootComponent] — source of truth for nav.
 * No Compose Navigation graph.
 */
@Composable
fun RootContent(
    component: RootComponent,
    modifier: Modifier = Modifier,
) {
    Children(
        stack = component.stack,
        modifier = modifier.fillMaxSize(),
        // No fade: TextureView/ExoPlayer ignores Compose alpha and would stay on top of Main (C-53).
    ) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Splash -> SplashScreen(instance.component)
            is RootComponent.Child.Main -> MainScaffold(instance.component)
        }
    }
}
