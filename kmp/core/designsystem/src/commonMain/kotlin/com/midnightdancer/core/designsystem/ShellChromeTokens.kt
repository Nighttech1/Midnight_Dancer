package com.midnightdancer.core.designsystem

/**
 * Named shell chrome slots for Android Compose / iOS SwiftUI (C-22 / C-23).
 * Shared owns dimensions + slot names only — no Compose/SwiftUI widgets.
 */
enum class ShellChromeSlot {
    /** Top app bar / navigation bar (main tabs chrome + Settings toolbar). */
    TopAppBar,

    /** Bottom tab bar — five Flutter NavTab destinations. */
    BottomTabBar,

    /** Primary scrollable content between top and bottom chrome. */
    Content,

    /** Settings push stack toolbar (same height as [TopAppBar] by default). */
    SettingsToolbar,
}

/**
 * Shell chrome dimensions in density-independent pixels (dp).
 * Platforms convert to `Dp` / `CGFloat`.
 */
object ShellChromeTokens {
    const val TopAppBarHeightDp: Float = 56f
    const val BottomTabBarHeightDp: Float = 64f
    const val ContentHorizontalPaddingDp: Float = 16f
    const val ContentVerticalPaddingDp: Float = 12f
    const val TabIconSizeDp: Float = 24f
    const val TabIndicatorHeightDp: Float = 2f
    const val MinTouchTargetDp: Float = 48f

    /** Slot → height (null = flexible / remaining space). */
    fun heightDpFor(slot: ShellChromeSlot): Float? =
        when (slot) {
            ShellChromeSlot.TopAppBar,
            ShellChromeSlot.SettingsToolbar,
            -> TopAppBarHeightDp
            ShellChromeSlot.BottomTabBar -> BottomTabBarHeightDp
            ShellChromeSlot.Content -> null
        }
}
