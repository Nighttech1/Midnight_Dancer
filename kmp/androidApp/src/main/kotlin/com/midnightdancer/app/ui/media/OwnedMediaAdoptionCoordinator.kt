package com.midnightdancer.app.ui.media

import com.midnightdancer.core.domain.media.*
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MediaAdoptionUi(
    val running: Boolean = false,
    val collecting: Boolean = false,
    val total: Int = 0,
    val processed: Int = 0,
    val protectedCount: Int = 0,
    val reused: Int = 0,
    val failed: Int = 0,
    val progress: OwnedMediaProgress? = null,
    val stats: OwnedMediaStats? = null,
    val warnings: List<String> = emptyList(),
    val issues: List<String> = emptyList(),
    val error: String? = null,
    val finished: Boolean = false,
    val cancelled: Boolean = false,
)

/** Explicit, cancellable per-reference adoption. Partial successes remain protected; no catalog/vault writes. */
class OwnedMediaAdoptionCoordinator(
    private val store: OwnedMediaStore,
    private val scope: CoroutineScope,
    private val collect: suspend () -> MediaAdoptionCollection,
) {
    private val mutable = MutableStateFlow(MediaAdoptionUi())
    val state = mutable.asStateFlow()
    private val generation = AtomicLong(0)
    private var job: Job? = null

    suspend fun refresh() {
        val token = generation.get()
        try {
            val stats = store.inspect()
            mutable.update { if (generation.get() == token) it.copy(stats = stats, error = null) else it }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { mutable.update { if (generation.get() == token) it.copy(error = ownedMediaMessage(error)) else it } }
    }
    fun start() {
        if (mutable.value.running) return
        val token = generation.incrementAndGet()
        mutable.value = MediaAdoptionUi(running = true, collecting = true, stats = mutable.value.stats)
        job = scope.launch(Dispatchers.IO) {
            try {
                val collection = collect()
                ensureActive()
                if (generation.get() != token) return@launch
                mutable.update { it.copy(collecting = false, total = collection.references.size, warnings = collection.warnings) }
                for (item in collection.references) {
                    ensureActive()
                    if (generation.get() != token) return@launch
                    try {
                        val owned = store.adopt(item.reference, item.kind) { progress ->
                            mutable.update { if (generation.get() == token) it.copy(progress = progress) else it }
                        }
                        ensureActive()
                        mutable.update { if (generation.get() == token) it.copy(processed = it.processed + 1,
                            protectedCount = it.protectedCount + 1, reused = it.reused + if (owned.reused) 1 else 0, progress = null) else it }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        mutable.update { if (generation.get() == token) it.copy(processed = it.processed + 1,
                            failed = it.failed + 1, progress = null,
                            issues = if (it.issues.size < 10) it.issues + "${item.title}: ${ownedMediaMessage(error)}" else it.issues) else it }
                    }
                }
                val stats = store.inspect()
                ensureActive()
                mutable.update { if (generation.get() == token) it.copy(running = false, finished = true, progress = null, stats = stats) else it }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutable.update { if (generation.get() == token) it.copy(running = false, collecting = false,
                    error = ownedMediaMessage(error), progress = null) else it }
            }
        }
    }
    fun cancel() {
        generation.incrementAndGet()
        job?.cancel()
        job = null
        mutable.update { if (it.running) it.copy(running = false, collecting = false, cancelled = true, progress = null) else it }
    }
}
