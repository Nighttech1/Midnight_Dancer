package com.midnightdancer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.storage.LowDiskDefaults
import com.midnightdancer.core.domain.usecase.ReadWriteUserPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.core.context.GlobalContext

/** When false (Trainer / Live), soft plaque is hidden. */
val LocalShowLowDiskBanner = staticCompositionLocalOf { true }

val LocalLowDiskMessage = compositionLocalOf<String?> { null }

val LocalHardDiskMessage = compositionLocalOf<String?> { null }

val LocalDismissSoftDiskPlaque = staticCompositionLocalOf<(() -> Unit)?> { null }

val LocalDismissHardDiskPlaque = staticCompositionLocalOf<(() -> Unit)?> { null }

val LocalRaiseHardDisk = staticCompositionLocalOf<((String) -> Unit)?> { null }

const val LOW_DISK_BANNER_TEXT: String =
    "Для стабильной работы приложения требуется освободить место на диске"

const val HARD_DISK_BANNER_TEXT: String =
    "Недостаточно места на диске — операция недоступна. Освободите память телефона"

private val PlaqueShape = RoundedCornerShape(14.dp)

@Composable
fun DiskSpacePlaque(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hard: Boolean = false,
) {
    val flame = ColorTokens.NeonOrange.toComposeColor()
    val border = if (hard) flame else Color.White.copy(alpha = 0.22f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PlaqueShape)
            .background(Color(0xF20A0B10))
            .border(1.dp, border, PlaqueShape)
            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = Color.White,
            fontFamily = MdFonts.Display,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = (-0.2).sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(flame.copy(alpha = 0.18f))
                .border(1.5.dp, flame, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PriorityHigh,
                contentDescription = "Внимание",
                tint = flame,
                modifier = Modifier.size(22.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Закрыть",
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun LowDiskBanner(
    message: String = LOW_DISK_BANNER_TEXT,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    hard: Boolean = false,
) {
    DiskSpacePlaque(
        message = message,
        onDismiss = onDismiss ?: {},
        modifier = modifier,
        hard = hard,
    )
}

class DiskPlaqueController {
    var softMessage by mutableStateOf<String?>(null)
        private set
    var hardMessage by mutableStateOf<String?>(null)
        private set
    var softDismissed by mutableStateOf(false)
        private set
    var hardDismissed by mutableStateOf(false)
        private set

    val softVisible: String?
        get() = softMessage?.takeIf { !softDismissed }
    val hardVisible: String?
        get() = hardMessage?.takeIf { !hardDismissed }

    fun applySoft(message: String?) {
        if (message != softMessage) softDismissed = false
        softMessage = message
    }

    fun applyHardAuto(message: String?) {
        if (message != null) {
            if (hardMessage != message) hardDismissed = false
            hardMessage = message
        } else if (hardMessage == HARD_DISK_BANNER_TEXT) {
            hardMessage = null
            hardDismissed = false
        }
    }

    fun dismissSoft() {
        softDismissed = true
    }

    fun dismissHard() {
        hardDismissed = true
    }

    fun raiseHard(message: String = HARD_DISK_BANNER_TEXT) {
        hardMessage = message
        hardDismissed = false
    }

    fun refreshFreeBytes(): Long {
        val koin = runCatching { GlobalContext.get() }.getOrNull()
        return koin?.let { runCatching { it.get<DeviceStorage>() }.getOrNull() }?.freeBytes() ?: 0L
    }
}

@Composable
fun rememberDiskPlaqueController(): DiskPlaqueController {
    val controller = remember { DiskPlaqueController() }
    LaunchedEffect(Unit) {
        while (isActive) {
            val koin = runCatching { GlobalContext.get() }.getOrNull()
            val storage = koin?.let { runCatching { it.get<DeviceStorage>() }.getOrNull() }
            val prefs = koin?.let { runCatching { it.get<ReadWriteUserPrefs>() }.getOrNull() }
            val free = storage?.freeBytes() ?: 0L
            val thr = prefs?.readLowDiskWarnBytes() ?: LowDiskDefaults.DEFAULT_WARN_BYTES
            val enabled = prefs?.readLowDiskWarnEnabled() ?: LowDiskDefaults.DEFAULT_WARN_ENABLED
            val soft =
                if (storage != null && enabled && LowDiskDefaults.shouldWarn(free, thr)) {
                    LOW_DISK_BANNER_TEXT
                } else {
                    null
                }
            controller.applySoft(soft)
            controller.applyHardAuto(
                if (storage != null && !LowDiskDefaults.canWriteMedia(free)) {
                    HARD_DISK_BANNER_TEXT
                } else {
                    null
                },
            )
            delay(10_000L)
        }
    }
    return controller
}

@Composable
fun ProvideLowDiskBanner(
    content: @Composable () -> Unit,
) {
    val controller = rememberDiskPlaqueController()
    CompositionLocalProvider(
        LocalLowDiskMessage provides controller.softVisible,
        LocalHardDiskMessage provides controller.hardVisible,
        LocalDismissSoftDiskPlaque provides controller::dismissSoft,
        LocalDismissHardDiskPlaque provides controller::dismissHard,
        LocalRaiseHardDisk provides { msg -> controller.raiseHard(msg) },
    ) {
        content()
    }
}

fun guardMediaDiskOrRaise(raiseHard: ((String) -> Unit)?): Boolean {
    val koin = runCatching { GlobalContext.get() }.getOrNull()
    val free =
        koin?.let { runCatching { it.get<DeviceStorage>() }.getOrNull() }?.freeBytes() ?: 0L
    if (LowDiskDefaults.canWriteMedia(free)) return true
    raiseHard?.invoke(HARD_DISK_BANNER_TEXT)
    return false
}
