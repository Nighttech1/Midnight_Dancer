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

    // C-292: muted boot video (half of user clip) → Continue → slide to Elements.
    if (state.show) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            SplashBootHost(
                onFinished = { component.onIntent(SplashIntent.Continue) },
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
