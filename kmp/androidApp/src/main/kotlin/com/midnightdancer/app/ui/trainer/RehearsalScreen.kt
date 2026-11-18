package com.midnightdancer.app.ui.trainer

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midnightdancer.app.BuildConfig
import com.midnightdancer.app.access.ProductAccessRuntime
import com.midnightdancer.app.access.ProOfferDialog
import com.midnightdancer.app.ui.components.MdFieldLabel
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultStore
import com.midnightdancer.feature.trainer.logic.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Part configuration lives under the selected choreography, never gates opening on audio. */
@Composable
fun RehearsalEntry(state: TrainerUiState, isTabSelected: Boolean = true) {
    if (state.mode != TrainerMode.Choreography) return
    val vault by ChoreographyVaultStore.items.collectAsStateWithLifecycle()
    val selected = remember(state.selectedChoreographyId, vault) {
        RehearsalSelectionResolver.resolve(state.selectedChoreographyId, vault)
    }
    val context = LocalContext.current
    val activity = remember(context) { context.rehearsalActivity() }
    val owner = remember(activity) {
        ViewModelProvider(activity, RetainedRehearsalViewModel.Factory(context.applicationContext))
            .get("trainer-part-session", RetainedRehearsalViewModel::class.java)
    }
    val runtime = remember(owner, selected.choreographyId, selected.planResult, state.selectedVoice) {
        owner.obtain(RehearsalRuntimeKey(selected.choreographyId, selected.planResult, state.selectedVoice))
    }
    key(runtime) { RehearsalPart(selected, isTabSelected, runtime) }
}

@Composable
private fun RehearsalPart(selected: RehearsalSelection, isTabSelected: Boolean, runtime: RetainedRehearsalSession) {
    val context = LocalContext.current
    val plan = selected.planResult.plan
    val scope = rememberCoroutineScope()
    val repository = remember { AndroidRehearsalSettingsRepository(context.applicationContext) }
    val controller = runtime.controller
    val session by controller.state.collectAsStateWithLifecycle()
    val access by ProductAccessRuntime.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var expanded by remember { mutableStateOf(true) }
    var offer by remember { mutableStateOf(false) }
    val voiceFailure by runtime.voiceFailure.collectAsStateWithLifecycle()
    DisposableEffect(runtime, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) controller.pause() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runtime.pauseForUiLeave()
        }
    }
    // Inline rehearsal pauses on dock leave; ordinary Live keeps its existing policy.
    LaunchedEffect(isTabSelected) { if (!isTabSelected) runtime.pauseForUiLeave() }
    LaunchedEffect(session.phase, session.pass) {
        Log.i("MdRehearsalQA", "phase=${session.phase} pass=${session.pass} positionMs=${session.positionMs}")
    }
    var start by remember { mutableStateOf("00:00,0") }
    var end by remember { mutableStateOf(formatRehearsalTime(plan?.durationMs ?: 0L)) }
    var exactEnd by remember { mutableStateOf(true) }
    var passes by remember { mutableStateOf("5") }
    var rest by remember { mutableStateOf("5") }
    // Existing saved preparation/voice preferences remain effective without extra form controls.
    var preparationMs by remember { mutableStateOf(3_000L) }
    var voiceEnabled by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var revisionNeedsConfirmation by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selected.choreographyId, plan?.revision) {
        loaded = false
        try {
            if (selected.canConfigure) (runtime.configuration ?: repository.read(selected.choreographyId))?.let { saved ->
                start = formatRehearsalTime(saved.startMs); end = formatRehearsalTime(saved.endMs)
                exactEnd = plan != null && saved.endMs == plan.durationMs
                passes = saved.totalPasses.toString(); rest = (saved.restMs / 1000).toString()
                preparationMs = saved.preparationMs; voiceEnabled = saved.voiceEnabled
                revisionNeedsConfirmation = plan != null && saved.choreographyRevision != plan.revision
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { formError = "Не удалось прочитать настройки. Сбросьте их и задайте часть заново." }
        loaded = true
    }
    fun settings(): RehearsalSettings? {
        val current = plan ?: return null
        val a = parseRehearsalTime(start) ?: return null
        val b = if (exactEnd) current.durationMs else parseRehearsalTime(end) ?: return null
        val n = passes.toIntOrNull() ?: return null
        val p = rest.toLongOrNull()?.takeIf { it in 0..120 } ?: return null
        return RehearsalSettings(current.choreographyId, current.revision, a, b, n, p * 1000, preparationMs, voiceEnabled)
    }
    val editing = !session.isWaitingForPlayer && session.phase in setOf(RehearsalPhase.Ready, RehearsalPhase.Error, RehearsalPhase.Completed)
    val enabled = access.canRehearse && selected.canConfigure && loaded && editing
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MdFieldLabel("Часть хореографии", Modifier.weight(1f))
            if (!access.canRehearse) TextButton(onClick = { offer = true }) { Text("PRO", color = AtelierColors.Gold) }
            else if (BuildConfig.DEBUG && access.testPro) TextButton(onClick = { ProductAccessRuntime.setTestPro(false) }) { Text("Free", color = Color.LightGray) }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(shape)
                .background(Color(0xE6121319))
                .border(1.dp, if (expanded && access.canRehearse) AtelierColors.Flame.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f), shape)
                .clickable(enabled = access.canRehearse && selected.canConfigure) {
                    focus.clearFocus(); keyboard?.hide()
                    if (expanded) controller.stop()
                    expanded = !expanded
                }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (!access.canRehearse) "Доступно в Pro" else "Начало — конец", fontFamily = MdFonts.Display,
                color = Color.White.copy(alpha = if (access.canRehearse) 1f else 0.4f), fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Часть хореографии",
                tint = if (access.canRehearse) AtelierColors.Gold else Color.Gray,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f))
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().alpha(if (access.canRehearse) 1f else 0.4f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PartField("Начало", start, { start = it; formError = null }, enabled, Modifier.weight(1f))
                    PartField("Конец", end, { end = it; exactEnd = false; formError = null }, enabled, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PartField("Количество проходов", passes, { passes = it; formError = null }, enabled, Modifier.weight(1f), numeric = true)
                    PartField("Отдых", rest, { rest = it; formError = null }, enabled, Modifier.weight(1f), numeric = true, suffix = "с")
                }
                if (!selected.canConfigure) Text(selected.planResult.error.orEmpty(), color = Color.LightGray, fontSize = 12.sp)
                if (revisionNeedsConfirmation) {
                    Text("Хореография изменена. Проверьте начало и конец.", color = AtelierColors.Gold)
                    TextButton(onClick = { revisionNeedsConfirmation = false }, enabled = enabled) { Text("Границы проверены") }
                }
                formError?.let { Text(it, color = Color(0xFFFFB4AB)) }
                session.error?.let { Text(it, color = Color(0xFFFFB4AB)) }
                voiceFailure?.let { Text(it, color = Color(0xFFFFB4AB)) }
                if (!editing) {
                    Text(if (session.pass == 0) "Подготовка" else "Проход ${session.pass} из ${session.totalPasses}", color = AtelierColors.Flame, fontSize = 20.sp)
                    Text(when {
                        session.isWaitingForPlayer -> "Подготовка музыки…"
                        session.phase == RehearsalPhase.Preparing -> "Подготовка: ${(session.remainingMs + 999) / 1000} с"
                        session.phase == RehearsalPhase.Resting -> "Отдых: ${(session.remainingMs + 999) / 1000} с"
                        session.phase == RehearsalPhase.Paused -> "Пауза"
                        else -> formatRehearsalTime(session.positionMs)
                    }, color = Color.White)
                    Text(session.activeLabels.joinToString(" · "), color = Color.White)
                    Button(onClick = { if (session.phase == RehearsalPhase.Paused) controller.resume() else controller.pause() }, modifier = Modifier.fillMaxWidth()) { Text(if (session.phase == RehearsalPhase.Paused) "Продолжить" else "Пауза") }
                    OutlinedButton(onClick = controller::stop, modifier = Modifier.fillMaxWidth()) { Text("Завершить") }
                } else {
                    if (session.phase == RehearsalPhase.Completed) Text("Готово: ${session.pass} из ${session.totalPasses}", color = AtelierColors.Flame)
                    Button(onClick = {
                        focus.clearFocus(); keyboard?.hide()
                        val value = settings()
                        formError = when {
                            plan == null -> selected.planResult.error ?: "Добавьте музыку и сохраните хореографию."
                            value == null -> "Проверьте начало, конец, количество проходов и отдых."
                            revisionNeedsConfirmation -> "Сначала проверьте и подтвердите границы изменённой хореографии."
                            else -> RehearsalValidator.errors(value, plan.durationMs, plan.revision).firstOrNull()
                        }
                        if (formError == null && value != null && plan != null && access.canRehearse) {
                            runtime.clearFailure(); runtime.start(value, plan)
                        }
                    }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AtelierColors.Flame, contentColor = Color.Black)) {
                        Text("Начать отработку")
                    }
                    if (access.canRehearse && selected.canConfigure) TextButton(onClick = {
                        scope.launch {
                            try {
                                repository.delete(selected.choreographyId)
                                runtime.clearConfiguration()
                                start = "00:00,0"; end = formatRehearsalTime(plan?.durationMs ?: 0); exactEnd = true
                                passes = "5"; rest = "5"; preparationMs = 3_000; voiceEnabled = true
                                revisionNeedsConfirmation = false; formError = null
                            } catch (cancelled: CancellationException) { throw cancelled
                            } catch (_: Exception) { formError = "Не удалось сбросить настройки. Попробуйте снова." }
                        }
                    }, enabled = loaded) { Text("Сбросить настройки") }
                }
            }
        }
    }
    if (offer) ProOfferDialog { offer = false }
}

@Composable
private fun PartField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean,
                      modifier: Modifier = Modifier, numeric: Boolean = false, suffix: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MdFieldLabel(label)
        OutlinedTextField(value, onChange, enabled = enabled, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontFamily = MdFonts.Display),
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            suffix = if (suffix != null) ({ Text(suffix, fontSize = 12.sp) }) else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                disabledTextColor = Color.LightGray, focusedContainerColor = Color(0xE6121319),
                unfocusedContainerColor = Color(0xE6121319), disabledContainerColor = Color(0xE6121319),
                focusedBorderColor = AtelierColors.Flame, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                disabledBorderColor = Color.White.copy(alpha = 0.12f), cursorColor = AtelierColors.Flame))
    }
}

internal fun parseRehearsalTime(text: String): Long? {
    val match = Regex("^(\\d{1,7}):([0-5]\\d)(?:[.,](\\d))?$").matchEntire(text.trim()) ?: return null
    val min = match.groupValues[1].toLongOrNull() ?: return null
    val sec = match.groupValues[2].toLong()
    val tenth = match.groupValues[3].toLongOrNull() ?: 0L
    return min * 60_000L + sec * 1000L + tenth * 100L
}

internal fun formatRehearsalTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0)
    return "%02d:%02d,%d".format(safe / 60_000L, safe / 1000L % 60L, safe / 100L % 10L)
}
