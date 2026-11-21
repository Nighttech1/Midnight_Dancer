package com.midnightdancer.shared

import com.midnightdancer.core.domain.settings.CreationAccessPolicy
import com.midnightdancer.feature.choreography.logic.ChoreographySnapshot
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultStore
import com.midnightdancer.feature.trainer.logic.*
import kotlinx.coroutines.*

/** Platform implementation MUST be Main-owned. No exported suspend conformance is needed in Swift. */
interface IosRehearsalAudioDriver {
    fun beginPrepare(plan: RehearsalPlan)
    fun isPrepared(): Boolean
    fun preparedDurationMs(): Long
    fun errorMessage(): String?
    fun takeInterruption(): Boolean
    fun seek(positionMs: Long)
    fun isReady(): Boolean
    fun play()
    fun isPlaying(): Boolean
    fun positionMs(): Long
    fun pause()
    fun stop()
}

interface IosRehearsalVoiceDriver {
    fun prepareVoice()
    fun isPrepared(): Boolean
    fun errorMessage(): String?
    fun speak(label: String)
    fun stop()
}

/** Implement only with a verified store/account source. Never map a debug flag to Granted. */
fun interface IosRehearsalAccessProvider {
    suspend fun access(): RehearsalAccess
}

/** No provider has been installed. Unavailable is deliberately distinct from a confirmed Free account. */
object IosUnavailableRehearsalAccess : IosRehearsalAccessProvider {
    override suspend fun access() = RehearsalAccess.Unavailable
}

/** Pre-existing libraries retain creation access; a fresh Free install has one editable choreography. */
internal class IosCreationPolicy(private val legacyProtected: Boolean) : CreationAccessPolicy {
    override fun canCreate(existingOwnChoreographies: Int) = legacyProtected || existingOwnChoreographies < 1
}

data class IosChoreographyChoice(val id: String, val title: String, val displayTitle: String)
data class IosChoreographySelection(val id: String, val title: String,
    val snapshot: ChoreographySnapshot? = null, val error: String? = null)

/** Exact IDs and authoritative snapshots; ordinary playback can preserve all clip trim/speed fields. */
object IosTrainerChoreographies {
    fun choices(): List<IosChoreographyChoice> {
        val all = ChoreographyVaultStore.all()
        val counts = all.groupingBy { it.title.trim().lowercase() }.eachCount()
        return all.map { IosChoreographyChoice(it.id, it.title,
            if (counts[it.title.trim().lowercase()] == 1) it.title else "${it.title} · ${it.id}") }
    }
    fun resolve(id: String): IosChoreographySelection {
        val item = ChoreographyVaultStore.byId(id)
            ?: return IosChoreographySelection(id, "", error = "Выбранная хореография недоступна")
        return IosChoreographySelection(item.id, item.title, item.snapshot,
            if (item.snapshot == null) "Откройте и сохраните эту хореографию перед запуском" else null)
    }
}

/** Retain one runtime per selection outside SwiftUI composition. Close before releasing platform drivers. */
class IosRehearsalRuntime(
    audio: IosRehearsalAudioDriver,
    voice: IosRehearsalVoiceDriver,
    access: IosRehearsalAccessProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var closed = false
    private val controller = RehearsalController(scope, IosRehearsalPlaybackPort(audio),
        IosRehearsalVoicePort(voice), RehearsalEntitlementPort { access.access() }, IosRehearsalSettingsStore)
    val state = controller.state
    private val interruptions = scope.launch {
        while (isActive) {
            if (audio.takeInterruption()) controller.pause()
            delay(20)
        }
    }
    var configuration: RehearsalSettings? = null
        private set

    fun selection(id: String) = RehearsalSelectionResolver.resolve(id, ChoreographyVaultStore.all())
    @Throws(CancellationException::class, Exception::class)
    suspend fun savedSettings(id: String): RehearsalSettings? = IosRehearsalSettingsStore.read(id)
    @Throws(CancellationException::class, Exception::class)
    suspend fun deleteSettings(id: String) {
        check(state.value.phase in setOf(RehearsalPhase.Ready, RehearsalPhase.Completed, RehearsalPhase.Error) && !state.value.isWaitingForPlayer) {
            "Сначала остановите отработку, затем сбросьте настройки."
        }
        IosRehearsalSettingsStore.delete(id)
        if (configuration?.choreographyId == id) configuration = null
    }
    /** Returns a configuration/selection error immediately; access denial is reported in controller state. */
    fun start(settings: RehearsalSettings): String? {
        check(!closed) { "Rehearsal runtime is closed" }
        val selection = selection(settings.choreographyId)
        val plan = selection.planResult.plan ?: return selection.planResult.error
            ?: "Не удалось подготовить постановку"
        val errors = RehearsalValidator.errors(settings, plan.durationMs, plan.revision)
        if (errors.isNotEmpty()) return errors.joinToString("\n")
        configuration = settings
        controller.start(settings, plan)
        return null
    }
    fun pause() { if (!closed) controller.pause() }
    fun resume() { if (!closed) controller.resume() }
    fun stop() { if (!closed) controller.stop() }
    fun close() {
        if (closed) return
        controller.stop()
        scope.cancel()
        closed = true
    }
}

internal class IosRehearsalPlaybackPort(private val driver: IosRehearsalAudioDriver) : RehearsalPlaybackPort {
    override suspend fun prepare(plan: RehearsalPlan): Long? {
        driver.beginPrepare(plan)
        try { await { driver.isPrepared() } } catch (cancelled: CancellationException) { driver.stop(); throw cancelled }
        return driver.preparedDurationMs().takeIf { it > 0 }
    }
    override suspend fun seekTo(positionMs: Long) { driver.seek(positionMs); awaitReady() }
    override suspend fun awaitReady() = await { driver.isReady() }
    override suspend fun playAndAwaitStarted() {
        driver.play()
        try { await { driver.isPlaying() } } catch (cancelled: CancellationException) { driver.pause(); throw cancelled }
    }
    override fun positionMs() = driver.positionMs()
    override fun pause() = driver.pause()
    override fun stop() = driver.stop()
    private suspend fun await(ready: () -> Boolean) {
        while (true) {
            currentCoroutineContext().ensureActive()
            driver.errorMessage()?.let { error(it) }
            if (ready()) return
            delay(10)
        }
    }
}

private class IosRehearsalVoicePort(private val driver: IosRehearsalVoiceDriver) : RehearsalVoicePort {
    override suspend fun prepare() {
        driver.prepareVoice()
        while (true) {
            currentCoroutineContext().ensureActive()
            driver.errorMessage()?.let { error(it) }
            if (driver.isPrepared()) return
            delay(10)
        }
    }
    override fun speak(label: String) = driver.speak(label)
    override fun stop() = driver.stop()
}
