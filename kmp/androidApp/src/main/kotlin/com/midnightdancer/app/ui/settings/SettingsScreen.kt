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
import com.midnightdancer.core.domain.styles.DanceStyleEntry
import com.midnightdancer.core.domain.styles.ReservedDanceStyle
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
    var stylePendingDelete by remember { mutableStateOf<DanceStyleEntry?>(null) }
    var showStyleCreate by remember { mutableStateOf(false) }
    var styleCreateName by remember { mutableStateOf("") }
    var styleCreateError by remember { mutableStateOf<String?>(null) }
    var styleCreatePending by remember { mutableStateOf<String?>(null) }
    var styleCreateSubmitted by remember { mutableStateOf(false) }

    LaunchedEffect(styleCreatePending, state.stylesBusy, state.stylesError, state.newStyleDraft, state.styleEntries) {
        val pending = styleCreatePending
        if (pending != null) {
            // Read the authoritative current value: Compose may conflate a quick busy->success transition.
            val current = component.uiState.value
            if (!current.stylesBusy) {
                if (current.stylesError != null) {
                    styleCreatePending = null
                } else if (current.newStyleDraft.isEmpty() && current.styleEntries.any { it.name == pending }) {
                    styleCreatePending = null
                    showStyleCreate = false
                    styleCreateName = ""
                }
            }
        }
    }

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
                            enabled = !state.stylesBusy && !state.stylesLoading,
                            onClick = {
                                styleCreateName = ""; styleCreateError = null
                                styleCreatePending = null; styleCreateSubmitted = false
                                component.onIntent(SettingsIntent.SetStyleDraft(""))
                                showStyleCreate = true
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_style_edit_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorTokens.TextSecondary.toComposeColor(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (state.stylesLoading) Text("Загрузка стилей…", color = Color.LightGray)
                    if (state.stylesBusy) Text("Сохранение изменений…", color = Color.LightGray)
                    state.stylesError?.let { Text(it, color = Color(0xFFFFB4AB)) }

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
                            state.styleEntries.forEach { entry ->
                                TagRow(
                                    name = entry.name,
                                    maxLength = state.styleNameMaxLength,
                                    flame = flame,
                                    enabled = !state.stylesBusy && !state.stylesLoading,
                                    editable = !entry.reserved,
                                    removable = !entry.reserved,
                                    removeCd = stringResource(R.string.settings_style_remove),
                                    onRename = { next ->
                                        component.onIntent(
                                            SettingsIntent.RenameStyleById(entry.id, next),
                                        )
                                    },
                                    onRemove = { if (entry.id != ReservedDanceStyle.ID) stylePendingDelete = entry },
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                com.midnightdancer.app.ui.media.OwnedMediaSettingsCard(
                    modifier = Modifier.padding(vertical = 8.dp),
                )

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

                // C-181: Sync + DEBUG seed blocks removed from Settings.

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showStyleCreate) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        val saving = state.stylesBusy || styleCreatePending != null
        val error = styleCreateError ?: state.stylesError.takeIf { styleCreateSubmitted }
        AlertDialog(
            onDismissRequest = { if (!saving) showStyleCreate = false },
            containerColor = Color(0xFF121319),
            title = { Text("Новый стиль", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = styleCreateName,
                        onValueChange = {
                            styleCreateName = it.take(state.styleNameMaxLength)
                            styleCreateError = null; styleCreateSubmitted = false
                        },
                        label = { Text("Название стиля") },
                        enabled = !saving,
                        singleLine = true,
                        isError = error != null,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = flame, cursorColor = flame,
                        ),
                    )
                    error?.let { Text(it, color = Color(0xFFFFB4AB)) }
                    if (saving) Text("Сохранение стиля…", color = Color.LightGray)
                }
            },
            confirmButton = {
                TextButton(enabled = !saving && !state.stylesLoading, onClick = {
                    val current = component.uiState.value
                    if (current.stylesBusy || current.stylesLoading || styleCreatePending != null) return@TextButton
                    val name = styleCreateName.trim()
                    styleCreateError = when {
                        name.isEmpty() -> "Введите название стиля."
                        current.styleEntries.any { it.name.equals(name, ignoreCase = true) } -> "Стиль с таким названием уже существует."
                        else -> null
                    }
                    if (styleCreateError == null) {
                        styleCreateSubmitted = true
                        styleCreatePending = name
                        component.onIntent(SettingsIntent.SetStyleDraft(name))
                        component.onIntent(SettingsIntent.AddStyle)
                    }
                }) { Text("Создать", color = flame, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { showStyleCreate = false }) {
                    Text("Отмена", color = Color.LightGray)
                }
            },
        )
    }

    stylePendingDelete?.let { style ->
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
                    text = "Удалить стиль «${style.name}»? Его элементы, музыка, хореографии и ваши сеты перейдут в «Без стиля». Материалы сохранятся. Добавленные архивы останутся без изменений.",
                    color = Color.White.copy(alpha = 0.85f),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.stylesBusy && !state.stylesLoading,
                    onClick = {
                        component.onIntent(SettingsIntent.RemoveStyleById(style.id))
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
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = rememberMdHaptics()
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(flame, RoundedCornerShape(12.dp))
            .interactiveSpringPress(
                enabled = enabled,
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
    enabled: Boolean = true,
    editable: Boolean = true,
    removable: Boolean = true,
) {
    var editing by remember(name) { mutableStateOf(false) }
    var hadFocusInEdit by remember(name) { mutableStateOf(false) }
    var draft by remember(name) { mutableStateOf(name) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(editing) {
        if (editing && enabled && editable) {
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
        if (editing && enabled && editable) {
            // C-184: double-tap rename → select entire name.
            com.midnightdancer.app.ui.components.MdSelectAllTextField(
                value = draft,
                onValueChange = { draft = it.take(maxLength) },
                singleLine = true,
                maxLength = maxLength,
                selectAllOnOpen = true,
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
                        if (focus.isFocused && editing) {
                            hadFocusInEdit = true
                        } else if (!focus.isFocused && editing && hadFocusInEdit) {
                            editing = false
                            hadFocusInEdit = false
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
                    .pointerInput(name, enabled, editable) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (enabled && editable) {
                                    hadFocusInEdit = false
                                    draft = name
                                    editing = true
                                }
                            },
                        )
                    },
            )
        }
        if (removable) IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = removeCd,
                tint = flame,
            )
        }
        else Text("Постоянный", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
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
