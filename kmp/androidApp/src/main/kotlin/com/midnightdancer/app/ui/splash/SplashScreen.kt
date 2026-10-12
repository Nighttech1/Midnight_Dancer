package com.midnightdancer.app.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.feature.splash.logic.SplashComponent
import com.midnightdancer.feature.splash.logic.SplashIntent

@Composable
fun SplashScreen(
    component: SplashComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.uiState.collectAsStateWithLifecycle()

    // Transparent while video is up — OledTrueBlack must not paint a shelf under Skip.
    // MainActivity Surface is also Transparent while Splash is active (C-59).
    if (state.show) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Transparent),
        ) {
            SplashBootHost(
                skipVisible = state.skipVisible,
                onSkip = { component.onIntent(SplashIntent.Continue) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ColorTokens.OledTrueBlack.toComposeColor()),
        )
    }
}
