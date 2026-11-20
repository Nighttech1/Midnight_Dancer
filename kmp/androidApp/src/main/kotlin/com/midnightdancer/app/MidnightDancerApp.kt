package com.midnightdancer.app

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.midnightdancer.app.debug.EdgeStressSeedCoordinator
import com.midnightdancer.app.debug.EdgeStressSeedReceiver
import com.midnightdancer.app.di.androidUiModules
import com.midnightdancer.app.ui.elements.ElementThumbCache
import com.midnightdancer.core.data.db.di.androidFileCatalogKoinModule
import com.midnightdancer.core.data.exchange.di.androidExchangeZipKoinModule
import com.midnightdancer.core.domain.usecase.LoadLibrary
import com.midnightdancer.feature.choreography.logic.ChoreoCatalog
import com.midnightdancer.shared.initKoinAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import android.util.AtomicFile
import java.io.File
import com.midnightdancer.core.domain.styles.ManageDanceStyles
import com.midnightdancer.core.domain.usecase.ReadWriteUserPrefs

data class StyleBootstrapState(val ready: Boolean = false, val busy: Boolean = true, val error: String? = null)

class MidnightDancerApp : Application(), SingletonImageLoader.Factory {
    private var edgeStressReceiver: EdgeStressSeedReceiver? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stylesStartup = MutableStateFlow(StyleBootstrapState())
    val styleBootstrapState = stylesStartup.asStateFlow()
    @Volatile private var stylesBootstrapStage = "idle"

    fun retryStyleBootstrap() {
        val previous = stylesStartup.value
        if (previous.busy || !stylesStartup.compareAndSet(previous, StyleBootstrapState())) return
        appScope.launch { attemptStylesBootstrap(); if (stylesStartup.value.ready) warmCatalogCaches() }
    }

    override fun onCreate() {
        super.onCreate()
        com.midnightdancer.app.access.ProductAccessRuntime.initialize(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (thread.name.contains("OkHttp", ignoreCase = true)) {
                Log.e("MdOkHttp", "background network failed (offline/emulator)", error)
                return@setDefaultUncaughtExceptionHandler
            }
            previous?.uncaughtException(thread, error)
        }
        initKoinAndroid(this)
        // C-13 / C-105: Room via core:data wrapper (no RoomDatabase.Builder in this module).
        // C-107: real exchange zip + secure import after catalog is on the graph.
        loadKoinModules(
            listOf(androidFileCatalogKoinModule(this)) +
                androidExchangeZipKoinModule() +
                androidUiModules(),
        )
        // Small durable alias registry is ready before any UI player/cache can read references.
        val owned = GlobalContext.get().get<com.midnightdancer.core.domain.media.OwnedMediaStore>()
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            com.midnightdancer.app.ui.media.OwnedMediaStartup.initialize(owned)
            attemptStylesBootstrap()
        }
        registerEdgeStressReceiverIfDebug()
        if (stylesStartup.value.ready) warmCatalogCaches()
    }

    private suspend fun attemptStylesBootstrap() {
        try {
            bootstrapStyles()
            stylesStartup.value = StyleBootstrapState(ready = true, busy = false)
            recordStylesDiagnostic(null)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            Log.e("MdStyles", "styles_bootstrap_failed:${error::class.simpleName}")
            recordStylesDiagnostic(error)
            stylesStartup.value = StyleBootstrapState(busy = false,
                error = "Не удалось подготовить стили и сохранённые материалы. Данные не очищены. Повторите попытку.")
        }
    }

    private suspend fun bootstrapStyles() {
        stylesBootstrapStage = "resolve_styles"
        val koin = GlobalContext.get()
        val styles = koin.get<ManageDanceStyles>()
        stylesBootstrapStage = "resolve_optional_preferences"
        // Production has historically omitted the optional DataStore module. A missing binding
        // keeps the previously visible default names; an actual read failure must still fail closed.
        val prefs = koin.getOrNull<ReadWriteUserPrefs>()
        bootstrapAndroidStyleCatalog(this, styles, prefs) { stylesBootstrapStage = it }
    }

    private fun recordStylesDiagnostic(error: Exception?) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            val text = buildString {
                append("stage=").append(stylesBootstrapStage).append('\n')
                append("outcome=").append(if (error == null) "success" else "failure").append('\n')
                var cause: Throwable? = error
                repeat(8) { index ->
                    val current = cause ?: return@repeat
                    // Deliberately omit exception messages: providers/JSON parsers may include user data.
                    append("cause[").append(index).append("]=").append(current.javaClass.name).append('\n')
                    current.stackTrace.take(24).forEach { frame ->
                        append(frame.className).append('#').append(frame.methodName)
                            .append('(').append(frame.fileName).append(':').append(frame.lineNumber).append(")\n")
                    }
                    cause = current.cause.takeUnless { it === current }
                }
            }.take(16 * 1024)
            val file = AtomicFile(File(filesDir, "styles_bootstrap_diagnostic_v1.txt"))
            val output = file.startWrite()
            try { output.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(output)
            } catch (failure: Throwable) { file.failWrite(output); throw failure }
        }
    }

    /** C-183 / C-185: stream-warm element thumbs + choreo picker catalog at process start. */
    private fun warmCatalogCaches() {
        val app = this
        appScope.launch {
            runCatching {
                val load = GlobalContext.getOrNull()?.getOrNull<LoadLibrary>() ?: return@launch
                val data = load.load()
                ChoreoCatalog.replaceFromAppData(data)
                val uris = data.danceStyles.flatMap { style ->
                    style.moves.mapNotNull { it.videoUri }
                }
                ElementThumbCache.warmAll(app, uris)
            }.onFailure { err ->
                Log.w("MdWarmCache", "catalog warm skipped", err)
            }
        }
    }

    /** DEBUG-only: adb broadcast seed for Client edge-stress QA (C-104). */
    private fun registerEdgeStressReceiverIfDebug() {
        if (!EdgeStressSeedCoordinator.isDebuggable(this)) return
        val receiver = EdgeStressSeedReceiver()
        val filter = IntentFilter(EdgeStressSeedCoordinator.ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        edgeStressReceiver = receiver
        Log.i("EdgeStressSeed", "receiver registered (debug)")
    }

    /** Process-wide Coil singleton — Compose AsyncImage uses this by default. */
    override fun newImageLoader(context: Context): ImageLoader =
        GlobalContext.get().get()
}

/** Same production bootstrap path, with explicit ports so an isolated no-prefs fixture can exercise it. */
internal suspend fun bootstrapAndroidStyleCatalog(
    context: Context,
    styles: ManageDanceStyles,
    prefs: ReadWriteUserPrefs?,
    stage: (String) -> Unit = {},
) {
        stage("read_vault")
        // Read the pre-bootstrap files without normalization; these names are migration inputs once.
        val vaultDisk = com.midnightdancer.app.choreography.AndroidChoreographyVaultPersister(context)
        val setsDisk = com.midnightdancer.app.exchange.AndroidSavedSetsPersister(context)
        val vaultRaw = vaultDisk.readRaw()
        stage("decode_vault")
        val vault = vaultRaw?.let { com.midnightdancer.feature.choreography.logic.decodeVaultItems(it) }.orEmpty()
        stage("read_sets")
        val sets = setsDisk.read().orEmpty()
        stage("read_preferences")
        val legacyPreferenceNames = prefs?.readDanceStyles()?.names
            ?: com.midnightdancer.core.domain.settings.DanceStyleConfig.DEFAULT_NAMES
        val names = legacyPreferenceNames + vault.flatMap { listOfNotNull(it.style, it.snapshot?.style) } +
            sets.filter { it.origin == com.midnightdancer.feature.exchange.logic.SavedSetOrigin.Mine }
                .map { it.styleName.ifBlank { it.styleId } }
        stage("bootstrap_room")
        styles.bootstrap(names)
        val snapshot = checkNotNull(styles.state.value)
        ChoreoCatalog.replaceStyleCatalog(snapshot)
        if (vaultRaw != null) {
            stage("project_vault")
            com.midnightdancer.app.choreography.AndroidChoreographyVaultPersister(context, styles).writeRaw(vaultRaw)
        }
        // Projection updates card metadata only; do not collect/rewrite archived payloads at startup.
        stage("project_sets")
        com.midnightdancer.app.exchange.AndroidSavedSetsPersister(context, styles).refreshStyles()
        stage("ready")
}
