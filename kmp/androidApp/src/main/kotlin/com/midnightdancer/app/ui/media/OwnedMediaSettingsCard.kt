package com.midnightdancer.app.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midnightdancer.app.ui.components.NeonCard
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.domain.media.OwnedMediaStore
import com.midnightdancer.core.domain.usecase.LoadLibrary
import org.koin.core.context.GlobalContext

@Composable
fun OwnedMediaSettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val store = remember { GlobalContext.get().get<OwnedMediaStore>() }
    val library = remember { GlobalContext.get().get<LoadLibrary>() }
    val references = remember(context, library) { OwnedMediaAdoptionReferences(context, library) }
    val coordinator = remember(store, scope, references) { OwnedMediaAdoptionCoordinator(store, scope, references::collect) }
    val state by coordinator.state.collectAsStateWithLifecycle()
    LaunchedEffect(coordinator) { coordinator.refresh() }
    DisposableEffect(coordinator) { onDispose { coordinator.cancel() } }
    NeonCard(accent = ColorTokens.NeonOrange, modifier = modifier) {
        Text("Хранение медиа", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("Сохраните доступные видео и музыку в приложении, чтобы удаление исходника на телефоне не сломало постановки. Одинаковое содержимое и повторы клипов используют одну копию.",
            style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
        Spacer(Modifier.height(8.dp))
        state.stats?.let { stats ->
            Text("Общие файлы: ${stats.poolFiles} · ${mediaBytesLabel(stats.poolBytes)}", color = Color.White)
            Text("Свободно: ${mediaBytesLabel(stats.freeBytes)}", color = Color.LightGray)
        }
        Text("Размер выше относится к общему хранилищу. Архивы сетов, старые импортированные файлы и кеши считаются отдельно. Исходники не удаляются; удаление приложения удалит и его копии.",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(Modifier.height(10.dp))
        if (state.running) {
            Text(if (state.collecting) "Проверяем библиотеку и постановки…" else "Обработано ${state.processed} из ${state.total}", color = Color.White)
            state.progress?.let { progress ->
                val total = progress.totalBytes
                Text(if (total != null) "${mediaBytesLabel(progress.copiedBytes)} из ${mediaBytesLabel(total)}"
                    else mediaBytesLabel(progress.copiedBytes), color = Color.LightGray)
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            TextButton(onClick = coordinator::cancel) { Text("Остановить сохранение") }
        } else {
            Button(onClick = coordinator::start, modifier = Modifier.fillMaxWidth()) {
                Text("Сохранить доступные материалы в приложении")
            }
        }
        if (state.finished || state.cancelled) {
            Spacer(Modifier.height(8.dp))
            Text(if (state.cancelled) "Остановлено. Уже сохранённые материалы защищены; остальные пока зависят от исходников."
                else if (state.failed > 0 || state.warnings.isNotEmpty()) "Защита неполная: часть материалов недоступна или не проверена."
                else if (state.total == 0) "Материалы для сохранения не найдены."
                else "Проверенные материалы сохранены в приложении. Исходники остались на телефоне.", color = Color.White)
            Text("Сохранено: ${state.protectedCount} · Использована готовая копия: ${state.reused} · Не удалось: ${state.failed}", color = Color.LightGray)
        }
        state.error?.let { Text(it, color = Color(0xFFFFB7A0)) }
        state.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB7A0)) }
        state.issues.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB7A0)) }
        if (state.failed > 10) Text("И ещё ${state.failed - 10}. Выберите недоступные файлы повторно в библиотеке.", color = Color.LightGray)
        if (state.running) Text("При выходе из настроек сохранение остановится. Готовые копии останутся.", color = Color.Gray)
    }
}
