package com.midnightdancer.core.designsystem

/** Stage 1 color token stubs — UI platforms map these later. */
object ColorTokens {
    const val MidnightNavy: Long = 0xFF0B1220
    const val AccentGold: Long = 0xFFC9A227
}

/** Platform implements real haptics; shared only declares the contract. */
fun interface HapticFeedback {
    fun performLight()
}
