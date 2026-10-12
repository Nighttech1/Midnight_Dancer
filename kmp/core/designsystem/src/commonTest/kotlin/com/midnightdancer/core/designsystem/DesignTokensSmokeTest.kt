package com.midnightdancer.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesignTokensSmokeTest {
    @Test
    fun moduleMarker_isStage3() {
        assertEquals("core-designsystem", DesignTokens.MODULE)
        assertEquals("stage3-c20", DesignTokens.STAGE)
    }

    @Test
    fun colorTokens_oledTrueBlackAndNeonAccents() {
        assertEquals(0xFF000000L, ColorTokens.OledTrueBlack)
        assertEquals(0xFFF97316L, ColorTokens.NeonOrange)
        assertEquals(0xFFFF6B35L, ColorTokens.NeonOrangeHot)
        assertEquals(0xFF00E5FFL, ColorTokens.NeonCyan)
        assertEquals(0xFFFF2D95L, ColorTokens.NeonMagenta)
        assertEquals(0xFFB8FF3CL, ColorTokens.NeonLime)
        assertEquals(0xFFFFFFFFL, ColorTokens.TextPrimary)
        // Stage 1 stubs retained
        assertEquals(0xFF0B1220L, ColorTokens.MidnightNavy)
        assertEquals(0xFFC9A227L, ColorTokens.AccentGold)
    }

    @Test
    fun typographyTokens_scalePositiveAndNamed() {
        assertTrue(TypographyTokens.All.isNotEmpty())
        TypographyTokens.All.forEach { token ->
            assertTrue(token.name.isNotBlank(), "blank name")
            assertTrue(token.sizeSp > 0f, token.name)
            assertTrue(token.lineHeightSp >= token.sizeSp, token.name)
        }
        assertEquals("display_large", TypographyTokens.DisplayLarge.name)
        assertEquals(34f, TypographyTokens.DisplayLarge.sizeSp)
        assertEquals("MidnightDisplay", TypographyTokens.FontFamilyDisplay)
        assertEquals("MidnightBody", TypographyTokens.FontFamilyBody)
    }

    @Test
    fun springTokens_navCriticalAndInteractiveUnderdamped() {
        val nav = SpringTokens.NavigationCritical
        assertEquals("navigation_critical", nav.id)
        assertEquals(1.0f, nav.dampingRatio)
        assertEquals(0.35f, nav.responseSeconds)
        assertEquals(1400f, nav.stiffness)

        val interactive = SpringTokens.InteractiveUnderdamped
        assertEquals("interactive_underdamped", interactive.id)
        assertTrue(interactive.dampingRatio < 1.0f)
        assertTrue(interactive.dampingRatio > 0f)
        assertEquals(0.28f, interactive.responseSeconds)
        assertEquals(900f, interactive.stiffness)

        assertEquals(2, SpringTokens.All.size)
    }

    @Test
    fun haptic_noOpDoesNotThrow() {
        NoOpHapticFeedback.perform(HapticEvent.LightImpact)
        NoOpHapticFeedback.performLight()
        NoOpHapticFeedback.perform(HapticEvent.Success)
        assertEquals(6, HapticEvent.entries.size)
        assertTrue(SpringTokens.InteractiveUnderdamped.approxSettleDelayMs() >= 16L)
        assertTrue(SpringTokens.NavigationCritical.approxSettleDelayMs() >= 16L)
    }

    @Test
    fun shellChrome_dimsAndSlots() {
        assertEquals(56f, ShellChromeTokens.TopAppBarHeightDp)
        assertEquals(64f, ShellChromeTokens.BottomTabBarHeightDp)
        assertEquals(16f, ShellChromeTokens.ContentHorizontalPaddingDp)
        assertEquals(48f, ShellChromeTokens.MinTouchTargetDp)

        assertEquals(56f, ShellChromeTokens.heightDpFor(ShellChromeSlot.TopAppBar))
        assertEquals(56f, ShellChromeTokens.heightDpFor(ShellChromeSlot.SettingsToolbar))
        assertEquals(64f, ShellChromeTokens.heightDpFor(ShellChromeSlot.BottomTabBar))
        assertNull(ShellChromeTokens.heightDpFor(ShellChromeSlot.Content))
        assertEquals(4, ShellChromeSlot.entries.size)
    }
}
