package com.midnightdancer.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.midnightdancer.app.ui.motion.MdMotion
import com.midnightdancer.app.ui.splash.SplashScreen
import com.midnightdancer.core.navigation.RootComponent

/**
 * Binds Compose to shared Decompose [RootComponent] — source of truth for nav.
 * C-292: Splash→Main uses horizontal slide (same soft feel as ChildPages tab swipe).
 * Exo TextureView is detached before Continue so it cannot float over the slide (C-53).
 */
@Composable
fun RootContent(
    component: RootComponent,
    modifier: Modifier = Modifier,
) {
    Children(
        stack = component.stack,
        modifier = modifier.fillMaxSize(),
        animation = stackAnimation(
            animator = slide(animationSpec = MdMotion.pagerSwipe),
        ),
    ) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Splash -> SplashScreen(instance.component)
            is RootComponent.Child.Main -> MainScaffold(instance.component)
        }
    }
}
