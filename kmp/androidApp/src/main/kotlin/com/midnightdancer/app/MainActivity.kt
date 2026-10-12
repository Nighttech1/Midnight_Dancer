package com.midnightdancer.app

import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.core.view.WindowCompat
import com.arkivanov.decompose.defaultComponentContext
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.midnightdancer.app.ui.RootContent
import com.midnightdancer.app.ui.splash.SplashSkipBridge
import com.midnightdancer.app.ui.theme.MidnightTheme
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.navigation.RootComponent
import com.midnightdancer.feature.splash.logic.SplashIntent
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

        // Let video paint under status/nav — no system contrast scrim shelf (C-59).
        // minSdk 29: contrast enforcement paints a translucent black band at the bottom.
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        root = get { parametersOf(defaultComponentContext()) }

        setContent {
            val stack by root.stack.subscribeAsState()
            val splashActive = stack.active.instance is RootComponent.Child.Splash

            MidnightTheme {
                // Transparent only while splash — video covers; rest of app keeps OLED black.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (splashActive) {
                        ComposeColor.Transparent
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

    /**
     * Compose AndroidView can swallow Skip taps. Activity sees the event first
     * only while Splash is the active child (C-53 / C-54).
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val splash = root.stack.value.active.instance as? RootComponent.Child.Splash
        if (splash != null && splash.component.uiState.value.skipVisible) {
            val skip = window.decorView.findViewById<TextView>(R.id.splash_skip)
            if (skip != null && skip.visibility == View.VISIBLE && skip.isShown) {
                // Bottom band + exact Skip hit — parent is full-screen FrameLayout (not a shelf).
                val band = ev.rawY >= resources.displayMetrics.heightPixels * 0.72f
                val inSkip = hit(skip, ev)
                if (band || inSkip) {
                    if (ev.actionMasked == MotionEvent.ACTION_UP) {
                        Log.i("MdSplashSkip", "skip tap bootReady=${splash.component.uiState.value.bootReady}")
                        SplashSkipBridge.detachVideo?.invoke()
                        splash.component.onIntent(SplashIntent.Continue)
                        Log.i("MdSplashSkip", "after child=${root.stack.value.active.instance::class.simpleName}")
                    }
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun hit(view: View, ev: MotionEvent): Boolean {
        val rect = Rect()
        return view.getGlobalVisibleRect(rect) &&
            rect.contains(ev.rawX.toInt(), ev.rawY.toInt())
    }
}
