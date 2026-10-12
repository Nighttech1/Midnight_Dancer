package com.midnightdancer.core.tts

import android.content.Context

/**
 * Holds [Context] for Android TTS (system + on-demand Piper paths).
 * Call [bindTtsAndroidContext] from app bootstrap (same pattern as media files).
 */
object TtsAndroidContext {
    @Volatile
    var applicationContext: Context? = null
}

fun bindTtsAndroidContext(context: Context) {
    TtsAndroidContext.applicationContext = context.applicationContext
}

fun requireTtsContext(): Context =
    TtsAndroidContext.applicationContext
        ?: error("bindTtsAndroidContext() must be called before createPlatformTtsEngine() on Android")
