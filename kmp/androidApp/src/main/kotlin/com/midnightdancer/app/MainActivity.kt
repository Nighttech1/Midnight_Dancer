package com.midnightdancer.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.core.view.WindowCompat
import com.arkivanov.decompose.defaultComponentContext
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.midnightdancer.app.ui.RootContent
import com.midnightdancer.app.ui.theme.MidnightTheme
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.navigation.RootComponent
import org.koin.android.ext.android.get
import org.koin.core.parameter.parametersOf

/**
 * Stage 3 (C-22): Decompose Root/Main + tab shells + Coil-wired Application.
 * Navigation source of truth = shared Decompose tree (not Compose Navigation).
 */
class MainActivity : ComponentActivity() {
    private lateinit var root: RootComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // C-282: logo splash once per process; Activity recreate after minimize → Main.
        val startAtMain = processSplashConsumed || savedInstanceState != null
        if (!startAtMain) processSplashConsumed = true
        val componentContext = defaultComponentContext()

        setContent {
            val app = application as MidnightDancerApp
            val styles by app.styleBootstrapState.collectAsStateWithLifecycle()
            if (!styles.ready) {
                MidnightTheme {
                    Surface(color = ComposeColor.Black, modifier = Modifier.fillMaxSize()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
                            Text(if (styles.busy) "Подготавливаем библиотеку…" else "Не удалось подготовить библиотеку", color = ComposeColor.White)
                            styles.error?.let { Text(it, color = ComposeColor.LightGray) }
                            if (!styles.busy) Button(onClick = app::retryStyleBootstrap) { Text("Повторить") }
                        }
                    }
                }
                return@setContent
            }
            val mediaFailure by com.midnightdancer.app.ui.media.OwnedMediaStartup.failure.collectAsStateWithLifecycle()
            if (mediaFailure != null) {
                MidnightTheme { com.midnightdancer.app.ui.media.OwnedMediaRecoveryScreen() }
                return@setContent
            }
            root = remember(componentContext) { get<RootComponent> { parametersOf(componentContext, startAtMain) } }
            val stack by root.stack.subscribeAsState()
            val splashActive = stack.active.instance is RootComponent.Child.Splash

            MidnightTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (splashActive) {
                        ComposeColor.Black
                    } else {
                        ColorTokens.OledTrueBlack.toComposeColor()
                    },
                ) {
                    BackHandler {
                        if (!root.onBack()) {
                            finish()
                        }
                    }
                    RootContent(component = root)
                }
            }
        }
    }

    companion object {
        /** Cleared when process dies; survives Activity recreate after Home. */
        private var processSplashConsumed: Boolean = false
    }
}
