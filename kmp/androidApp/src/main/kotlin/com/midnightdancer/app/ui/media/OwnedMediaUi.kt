package com.midnightdancer.app.ui.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midnightdancer.core.data.files.OwnedMediaRuntime
import com.midnightdancer.core.domain.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

fun ownedMediaMessage(error: Throwable): String = when ((error as? OwnedMediaException)?.reason) {
    OwnedMediaFailure.Unavailable -> "Не удалось открыть исходный файл. Выберите его повторно."
    OwnedMediaFailure.NoSpace -> "Не хватает свободного места для сохранения файла в приложении."
    OwnedMediaFailure.Unsupported -> "Формат файла не поддерживается. Для видео используйте MP4/MOV, для музыки — MP3/M4A/WAV/AAC/OGG/FLAC."
    OwnedMediaFailure.CorruptOwned -> "Не удалось проверить сохранённый файл или хранилище. Прежние данные оставлены без изменений."
    else -> "Не удалось сохранить файл. Прежние материалы не изменены. Попробуйте снова."
}

/** Startup failure is shown before composing any page/player; no identity fallback after lost ledger. */
object OwnedMediaStartup {
    private val mutable = MutableStateFlow<OwnedMediaException?>(null)
    val failure = mutable.asStateFlow()
    suspend fun initialize(store: OwnedMediaStore) {
        try {
            store.initialize()
            OwnedMediaRuntime.bind(store)
            mutable.value = null
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            val typed = error as? OwnedMediaException ?: OwnedMediaException(OwnedMediaFailure.IO, error)
            OwnedMediaRuntime.bindFailure(typed)
            mutable.value = typed
        }
    }
}

@Composable
fun OwnedMediaRecoveryScreen() {
    val scope = rememberCoroutineScope()
    val failure by OwnedMediaStartup.failure.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Хранилище медиа недоступно", color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(failure?.let(::ownedMediaMessage).orEmpty(), color = Color.LightGray)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                scope.launch {
                    busy = true
                    withContext(Dispatchers.IO) { OwnedMediaStartup.initialize(GlobalContext.get().get<OwnedMediaStore>()) }
                    busy = false
                }
            }, enabled = !busy) { Text(if (busy) "Проверяем…" else "Повторить") }
        }
    }
}

data class OwnedCopyUi(val busy: Boolean = false, val progress: OwnedMediaProgress? = null, val error: String? = null)

class OwnedMediaCopyController internal constructor(private val store: OwnedMediaStore, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(OwnedCopyUi())
    val state = mutable.asStateFlow()
    private val generation = AtomicLong(0)
    private var job: Job? = null
    fun save(reference: String, kind: OwnedMediaKind, existingReference: Boolean = false, onReady: suspend (OwnedMedia) -> Unit) {
        if (mutable.value.busy) return
        val token = generation.incrementAndGet()
        mutable.value = OwnedCopyUi(busy = true)
        job = scope.launch {
            try {
                val update: (OwnedMediaProgress) -> Unit = { progress ->
                    if (generation.get() == token) mutable.value = OwnedCopyUi(busy = true, progress = progress)
                }
                val owned = if (existingReference) store.adopt(reference, kind, update) else store.retain(reference, kind, update)
                ensureActive()
                if (generation.get() != token) return@launch
                onReady(owned)
                if (generation.get() == token) mutable.value = OwnedCopyUi()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (generation.get() == token) mutable.value = OwnedCopyUi(error = ownedMediaMessage(error))
            }
        }
    }
    fun cancel() { generation.incrementAndGet(); job?.cancel(); job = null; mutable.value = OwnedCopyUi() }
}

@Composable
fun rememberOwnedMediaCopyController(): OwnedMediaCopyController {
    val scope = rememberCoroutineScope()
    val store = remember { GlobalContext.get().get<OwnedMediaStore>() }
    val controller = remember(store, scope) { OwnedMediaCopyController(store, scope) }
    DisposableEffect(controller) { onDispose { controller.cancel() } }
    return controller
}

fun mediaBytesLabel(bytes: Long): String = if (bytes >= 1024L * 1024 * 1024) "%.2f ГБ".format(bytes / (1024.0 * 1024 * 1024)) else "%.1f МБ".format(bytes / (1024.0 * 1024))

@Composable
fun OwnedMediaCopyDialog(controller: OwnedMediaCopyController) {
    val state by controller.state.collectAsStateWithLifecycle()
    if (state.busy) AlertDialog(
        onDismissRequest = controller::cancel,
        title = { Text("Сохраняем файл в приложении") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Исходник останется на телефоне. Повторы в постановках используют одну сохранённую копию.")
                val progress = state.progress
                val total = progress?.totalBytes
                Text(if (progress != null && total != null) "${mediaBytesLabel(progress.copiedBytes)} из ${mediaBytesLabel(total)}" else mediaBytesLabel(progress?.copiedBytes ?: 0))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = controller::cancel) { Text("Отменить копирование") } },
    )
}

suspend fun ownedMediaDuration(context: Context, reference: String): Long = withContext(Dispatchers.IO) {
    MediaMetadataRetriever().use { reader ->
        val uri = if (reference.startsWith("/")) Uri.fromFile(File(reference)) else Uri.parse(reference)
        reader.setDataSource(context, uri)
        reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
            ?: throw OwnedMediaException(OwnedMediaFailure.Unsupported)
    }
}

@Composable
fun MediaReferenceNotice(reference: String?, explicitNewPick: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var message by remember(reference, explicitNewPick) { mutableStateOf<String?>(null) }
    var missing by remember(reference, explicitNewPick) { mutableStateOf(false) }
    LaunchedEffect(reference, explicitNewPick) {
        if (reference.isNullOrBlank()) { message = null; return@LaunchedEffect }
        if (explicitNewPick) { message = "Видео сохранится в приложении после нажатия «Сохранить»."; return@LaunchedEffect }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val store = GlobalContext.get().get<OwnedMediaStore>()
                val effective = store.resolveReference(reference)
                if (store.isDurableOwnedReference(effective)) "Файл сохранён в приложении. Удаление исходника не повредит ему." to false
                else {
                    val uri = Uri.parse(effective)
                    val available = if (uri.scheme == "content") context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } == true
                        else File(if (uri.scheme == "file") uri.path.orEmpty() else effective).isFile
                    if (available) "Пока используется исходный файл. Его копию можно сохранить в приложении." to false
                    else "Исходный файл недоступен. Выберите его повторно." to true
                }
            }.getOrElse { ownedMediaMessage(it) to true }
        }
        message = result.first; missing = result.second
    }
    message?.let { Text(it, color = if (missing) Color(0xFFFFB4AB) else Color.LightGray, style = MaterialTheme.typography.bodySmall) }
}
