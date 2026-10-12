package com.midnightdancer.app.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.midnightdancer.core.designsystem.ShellChromeTokens
import com.midnightdancer.core.navigation.NavTab

/**
 * Soft / hard disk plaques dock-flush above bottom menu (C-111 / C-112).
 * Soft hidden on Trainer; hard always eligible when message set.
 */
@Composable
fun BoxScope.DiskPlaqueHost(
    selectedTab: NavTab?,
    settingsOverlay: Boolean,
    modifier: Modifier = Modifier,
) {
    val showSoftSlot = settingsOverlay || (selectedTab != null && selectedTab != NavTab.TRAINER)
    val soft = LocalLowDiskMessage.current?.takeIf { showSoftSlot && LocalShowLowDiskBanner.current }
    val hard = LocalHardDiskMessage.current
    val message = hard ?: soft
    val hardMode = hard != null
    if (message.isNullOrBlank()) return

    val dismiss =
        if (hardMode) {
            LocalDismissHardDiskPlaque.current
        } else {
            LocalDismissSoftDiskPlaque.current
        }

    // Overlay sits above Scaffold+dock: pad by dock height so plaque hugs menu buttons.
    val dockPad =
        if (settingsOverlay) {
            16.dp
        } else {
            ShellChromeTokens.BottomTabBarHeightDp.dp + 10.dp
        }

    DiskSpacePlaque(
        message = message,
        onDismiss = { dismiss?.invoke() },
        hard = hardMode,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(bottom = dockPad)
            .fillMaxWidth(),
    )
}
