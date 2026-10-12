package com.midnightdancer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.navigation.NavTab

private val DockShape = RoundedCornerShape(26.dp)
private val DockFill = Color(0xE60C0D12)
private val Idle = Color.White.copy(alpha = 0.45f)
private val IconSize = 30.dp
private val SlotHeight = 56.dp

/** Stitch kinetic atelier floating glass tabbar — icons only, selected = Flame glow. */
@Composable
fun CyberAcousticDock(
    selected: NavTab,
    onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flame = ColorTokens.NeonOrange.toComposeColor()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, DockShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(DockShape)
                .background(DockFill)
                .border(1.dp, Color.White.copy(alpha = 0.10f), DockShape)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab.ordered.forEach { tab ->
                val on = tab == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(SlotHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(tab) }
                        .semantics { contentDescription = tab.dockLabel() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(flame.copy(alpha = 0.16f)),
                        )
                    }
                    Icon(
                        imageVector = tab.dockIcon(),
                        contentDescription = null,
                        tint = if (on) flame else Idle,
                        modifier = Modifier.size(IconSize),
                    )
                }
            }
        }
    }
}

private fun NavTab.dockLabel(): String =
    when (this) {
        NavTab.ELEMENTS -> "Каталог"
        NavTab.MUSIC -> "Музыка"
        NavTab.CHOREOGRAPHY -> "Связки"
        NavTab.TRAINER -> "Тренинг"
        NavTab.EXCHANGE -> "Обмен"
    }

private fun NavTab.dockIcon(): ImageVector =
    when (this) {
        NavTab.ELEMENTS -> Icons.AutoMirrored.Filled.List
        NavTab.MUSIC -> Icons.Filled.MusicNote
        NavTab.CHOREOGRAPHY -> Icons.Filled.Groups
        NavTab.TRAINER -> Icons.Filled.Bolt
        NavTab.EXCHANGE -> Icons.Filled.SwapHoriz
    }
