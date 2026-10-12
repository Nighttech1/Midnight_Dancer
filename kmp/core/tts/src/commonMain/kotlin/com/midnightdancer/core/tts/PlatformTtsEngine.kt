package com.midnightdancer.core.tts

/**
 * Platform factory — Android TextToSpeech / iOS AVSpeech + Piper probe.
 * Call [bindTtsAndroidContext] on Android before first use.
 */
expect fun createPlatformTtsEngine(): TtsEngine
