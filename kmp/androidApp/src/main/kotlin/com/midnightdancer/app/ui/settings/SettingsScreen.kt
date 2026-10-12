package com.midnightdancer.app.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midnightdancer.app.BuildConfig
import com.midnightdancer.app.R
import com.midnightdancer.app.debug.EdgeStressSeedCoordinator
import com.midnightdancer.app.ui.components.NeonActionButton
import com.midnightdancer.app.ui.components.NeonCard
import com.midnightdancer.app.ui.components.SettingsToggleRow
import com.midnightdancer.app.ui.components.ShellEnter
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.data.debug.EdgeStressCatalogSeeder
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.ShellChromeTokens
import com.midnightdancer.feature.settings.logic.SettingsComponent
import com.midnightdancer.feature.settings.logic.SettingsIntent
import kotlinx.coroutines.launch

private val LanguageOptions = listOf(
    "ru" to "Русский",
    "en" to "English",
    "es" to "Español",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    component: SettingsComponent,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by component.uiState.collectAsStateWithLifecycle()
    val timeLabel =
        "${state.reminderHour}:${state.reminderMinute.toString().padStart(2, '0')}"
    val flame = ColorTokens.NeonOrange.toComposeColor()
    val haptics = rememberMdHaptics()
    var stylePendingDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ColorTokens.OledTrueBlack.toComposeColor(),
        topBar = {
            TopAppBar(
                title = {
                    com.midnightdancer.app.ui.components.MdPageTitle(
                        text = state.title,
                        attachLowDiskBanner = false,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ColorTokens.OledTrueBlack.toComposeColor(),
                    titleContentColor = ColorTokens.TextPrimary.toComposeColor(),
                    navigationIconContentColor = flame,
                ),
            )
        },
    ) { padding ->
        ShellEnter(
            fillMax = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(
                    horizontal = ShellChromeTokens.ContentHorizontalPaddingDp.dp,
                    vertical = ShellChromeTokens.ContentVerticalPaddingDp.dp,
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                NeonCard(accent = ColorTokens.NeonOrange) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Язык интерфейса",
                                style = MaterialTheme.typography.titleMedium,
                                color = ColorTokens.TextPrimary.toComposeColor(),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Выбор сохраняется через prefs, когда DataStore доступен.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ColorTokens.TextSecondary.toComposeColor(),
                            )
                        }
                        LanguageDropdown(
                            selectedCode = state.languageCode,
                            onSelect = { code ->
                                component.onIntent(SettingsIntent.SetLanguageStub(code))
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "Напоминание о танце",
                    subtitle = if (state.reminderEnabled) {
                        "Включено · ежедневно в $timeLabel"
                    } else {
                        "Выключено · мягкий пинок в зал"
                    },
                    enabled = state.reminderEnabled,
                    accent = ColorTokens.NeonOrange,
                    onToggle = {
                        component.onIntent(
                            SettingsIntent.SetReminderEnabledStub(!state.reminderEnabled),
                        )
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

                NeonCard(accent = ColorTokens.NeonOrange) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_styles_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = ColorTokens.TextPrimary.toComposeColor(),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    R.string.settings_styles_hint,
                                    state.styleNameMaxLength,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorTokens.TextSecondary.toComposeColor(),
                            )
                        }
                        AddTagPill(
                            label = stringResource(R.string.settings_style_add),
                            flame = flame,
                            onClick = { component.onIntent(SettingsIntent.AddStyle) },
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_style_edit_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorTokens.TextSecondary.toComposeColor(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // C-75: ~5 TagRow visible (≈48dp each + gaps), then vertical scroll.
                    PremiumTagScrollHost(
                        flame = flame,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp, max = 252.dp),
                    ) { scrollModifier ->
                        Column(
                            modifier = scrollModifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            state.danceStyles.forEach { name ->
                                TagRow(
                                    name = name,
                                    maxLength = state.styleNameMaxLength,
                                    flame = flame,
                                    removeCd = stringResource(R.string.settings_style_remove),
                                    onRename = { next ->
                                        component.onIntent(
                                            SettingsIntent.RenameStyle(name, next),
                                        )
                                    },
                                    onRemove = { stylePendingDelete = name },
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                NeonCard(accent = ColorTokens.NeonOrange) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_tags_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = ColorTokens.TextPrimary.toComposeColor(),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    R.string.settings_tags_hint,
                                    state.categoryNameMaxLength,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorTokens.TextSecondary.toComposeColor(),
                            )
                        }
                        AddTagPill(
                            label = stringResource(R.string.settings_tag_add),
                            flame = flame,
                            onClick = { component.onIntent(SettingsIntent.AddCategory) },
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_tag_edit_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorTokens.TextSecondary.toComposeColor(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    PremiumTagScrollHost(
                        flame = flame,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 280.dp),
                    ) { scrollModifier ->
                        Column(
                            modifier = scrollModifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            state.elementCategories.forEach { name ->
                                TagRow(
                                    name = name,
                                    maxLength = state.categoryNameMaxLength,
                                    flame = flame,
                                    removeCd = stringResource(R.string.settings_tag_remove),
                                    onRename = { next ->
                                        component.onIntent(
                                            SettingsIntent.RenameCategory(name, next),
                                        )
                                    },
                                    onRemove = {
                                        component.onIntent(SettingsIntent.RemoveCategory(name))
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                NeonCard(accent = ColorTokens.NeonOrange) {
                    Text(
                        text = "Синхронизация",
                        style = MaterialTheme.typography.titleMedium,
                        color = ColorTokens.TextPrimary.toComposeColor(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (state.prefsLoaded) {
                            "Prefs загружены · язык ${state.languageCode.uppercase()}"
                        } else {
                            "Загрузка prefs…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorTokens.TextSecondary.toComposeColor(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeonActionButton(
                        label = "Обновить из prefs",
                        accent = ColorTokens.NeonOrange,
                        onClick = { component.onIntent(SettingsIntent.ReloadFromPrefs) },
                        solidFill = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.height(12.dp))
                    EdgeStressDebugSeedCard()
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    stylePendingDelete?.let { styleName ->
        AlertDialog(
            onDismissRequest = { stylePendingDelete = null },
            containerColor = Color(0xFF121319),
            title = {
                Text(
                    text = stringResource(R.string.settings_style_delete_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_style_delete_message),
                    color = Color.White.copy(alpha = 0.85f),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        component.onIntent(SettingsIntent.RemoveStyle(styleName))
                        stylePendingDelete = null
                        haptics.perform(HapticEvent.MediumImpact)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.settings_style_delete_confirm),
                        color = flame,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { stylePendingDelete = null }) {
                    Text(
                        text = stringResource(R.string.settings_style_delete_cancel),
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            },
        )
    }
}

@Composable
private fun EdgeStressDebugSeedCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    NeonCard(accent = ColorTokens.NeonOrange) {
        Text(
            text = "DEBUG · Edge stress seed",
            style = MaterialTheme.typography.titleMedium,
            color = ColorTokens.TextPrimary.toComposeColor(),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Вставить ${EdgeStressCatalogSeeder.DEFAULT_COUNT}+ stub элементов и треков " +
                "(Room + UI). Только debug-сборка.",
            style = MaterialTheme.typography.bodyMedium,
            color = ColorTokens.TextSecondary.toComposeColor(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        NeonActionButton(
            label = if (busy) "Сеем…" else "Seed ${EdgeStressCatalogSeeder.DEFAULT_COUNT} stubs",
            accent = ColorTokens.NeonOrange,
            onClick = {
                if (busy) return@NeonActionButton
                busy = true
                scope.launch {
                    try {
                        val result =
                            EdgeStressSeedCoordinator.seed(
                                context = context.applicationContext,
                                count = EdgeStressCatalogSeeder.DEFAULT_COUNT,
                            )
                        Toast.makeText(
                            context,
                            "Seeded ${result.moveCount} el / ${result.songCount} tracks",
                            Toast.LENGTH_LONG,
                        ).show()
                    } catch (t: Throwable) {
                        Toast.makeText(
                            context,
                            "Seed failed: ${t.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    } finally {
                        busy = false
                    }
                }
            },
            solidFill = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AddTagPill(
    label: String,
    flame: Color,
    onClick: () -> Unit,
) {
    val haptics = rememberMdHaptics()
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(flame, RoundedCornerShape(12.dp))
            .interactiveSpringPress(
                pressedScale = 0.94f,
                hapticEvent = HapticEvent.MediumImpact,
                haptics = haptics,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            fontSize = 20.sp,
        )
    }
}

@Composable
private fun TagRow(
    name: String,
    maxLength: Int,
    flame: Color,
    removeCd: String = stringResource(R.string.settings_tag_remove),
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var editing by remember(name) { mutableStateOf(false) }
    var draft by remember(name) { mutableStateOf(name) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(editing) {
        if (editing) {
            draft = name
            focusRequester.requestFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.take(maxLength) },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                cursorBrush = SolidColor(flame),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focus ->
                        if (!focus.isFocused && editing) {
                            editing = false
                            val cleaned = draft.trim()
                            if (cleaned.isNotEmpty() && cleaned != name) {
                                onRename(cleaned)
                            } else {
                                draft = name
                            }
                        }
                    },
            )
        } else {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = ColorTokens.TextPrimary.toComposeColor(),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(name) {
                        detectTapGestures(
                            onDoubleTap = { editing = true },
                        )
                    },
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = removeCd,
                tint = flame,
            )
        }
    }
}

@Composable
private fun LanguageDropdown(
    selectedCode: String,
    onSelect: (String) -> Unit,
) {
    val haptics = rememberMdHaptics()
    var expanded by remember { mutableStateOf(false) }
    val label = LanguageOptions.firstOrNull { it.first == selectedCode }?.second
        ?: LanguageOptions.first().second
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "lang-chevron",
    )
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(AtelierColors.Flame)
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .interactiveSpringPress(
                    pressedScale = 0.96f,
                    hapticEvent = HapticEvent.Selection,
                    haptics = haptics,
                    onClick = { expanded = true },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevron),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color(0xF0121319),
        ) {
            LanguageOptions.forEach { (code, name) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = name,
                            color = if (code == selectedCode) AtelierColors.Flame else Color.White,
                            fontWeight = if (code == selectedCode) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Medium
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        haptics.perform(HapticEvent.Selection)
                        onSelect(code)
                    },
                )
            }
        }
    }
}
