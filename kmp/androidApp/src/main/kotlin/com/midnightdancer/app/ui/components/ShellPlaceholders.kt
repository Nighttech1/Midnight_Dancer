package com.midnightdancer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.ShellChromeTokens
import org.koin.core.context.GlobalContext

@Composable
fun TabShellScaffold(
    title: String,
    subtitle: String? = null,
    accent: Long = ColorTokens.NeonOrange,
    modifier: Modifier = Modifier,
    headerExtra: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ShellEnter(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = ShellChromeTokens.ContentHorizontalPaddingDp.dp,
                vertical = ShellChromeTokens.ContentVerticalPaddingDp.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = ColorTokens.TextPrimary.toComposeColor(),
                    fontWeight = FontWeight.Bold,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorTokens.TextSecondary.toComposeColor(),
                    )
                }
            }
            NeonPulseBars(accent = accent)
        }
        Spacer(modifier = Modifier.height(10.dp))
        AccentHairline(accent = accent)
        headerExtra?.let {
            Spacer(modifier = Modifier.height(12.dp))
            it()
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            content = content,
        )
    }
}

/** Legacy alias — routes to staggered neon cards (C-28). */
@Composable
fun PlaceholderItemList(
    items: List<String>,
    emptyLabel: String,
    modifier: Modifier = Modifier,
    accent: Long = ColorTokens.NeonOrange,
    leadingMeta: ((index: Int, label: String) -> String)? = null,
) {
    StaggeredNeonList(
        items = items,
        emptyLabel = emptyLabel,
        accent = accent,
        modifier = modifier,
        leadingMeta = leadingMeta,
    )
}

/**
 * Coil ImageLoader wiring (local Android resource — no network).
 * Framed as a neon gallery tile for Stage 4 shells.
 */
@Composable
fun CoilWirePlaceholder(
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = remember {
        GlobalContext.get().get()
    },
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ColorTokens.SurfaceElevated.toComposeColor())
            .semantics { contentDescription = "Style gallery preview" },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(android.R.drawable.ic_menu_gallery)
                .crossfade(true)
                .build(),
            contentDescription = null,
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        )
    }
}
