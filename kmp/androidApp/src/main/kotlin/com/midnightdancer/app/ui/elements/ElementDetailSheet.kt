package com.midnightdancer.app.ui.elements

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.midnightdancer.app.ui.theme.MdFonts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.midnightdancer.app.R
import com.midnightdancer.feature.elements.logic.ElementCard

private val SheetShape = RoundedCornerShape(28.dp)
private val ChromaGreen = Color(0xFF00FF66)

@Composable
fun ElementDetailSheet(
    card: ElementCard,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(card.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.fromUri(
                    Uri.parse("android.resource://${context.packageName}/${R.raw.splash_boot}"),
                ),
            )
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(card.id) {
        onDispose { player.release() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clip(SheetShape)
                .background(Color(0xF00C0D12))
                .border(1.dp, Color.White.copy(alpha = 0.12f), SheetShape)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = MdFonts.Display,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = card.tags.ifEmpty { listOf(card.levelLabel) }.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = AtelierColors.Flame,
            )
            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ChromaGreen),
            ) {
                // Chroma-key plate under the video (green screen reference).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ChromaGreen),
                )
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 10.dp),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = true
                            this.player = player
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.elements_description),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.55f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = card.description.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.elements_close), color = AtelierColors.Flame)
            }
        }
    }
}
