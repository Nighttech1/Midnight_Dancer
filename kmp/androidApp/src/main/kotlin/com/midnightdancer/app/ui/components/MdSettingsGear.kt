package com.midnightdancer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.midnightdancer.app.R
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback

/**
 * Canonical settings gear (C-151) — 40.dp glass circle + Flame Settings icon.
 * Same as Elements; place at top-end next to [MdPageTitle].
 */
@Composable
fun MdSettingsGear(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    haptics: HapticFeedback = rememberMdHaptics(),
) {
    val cd = stringResource(R.string.elements_tune)
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .interactiveSpringPress(
                pressedScale = 0.90f,
                hapticEvent = HapticEvent.Selection,
                haptics = haptics,
                onClick = onClick,
            )
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = AtelierColors.Flame,
            modifier = Modifier.size(20.dp),
        )
    }
}
