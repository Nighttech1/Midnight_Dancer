package com.midnightdancer.feature.trainer.logic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

/**
 * A single owner (normally Main) calls commands; playback callbacks never change state directly.
 * The owner cancels this controller before releasing adapters. No UI composition owns the timers.
 */
class RehearsalController(
    private val scope: CoroutineScope,
    private val playback: RehearsalPlaybackPort,
    private val voice: RehearsalVoicePort,
    private val entitlements: RehearsalEntitlementPort,
    private val settingsRepository: RehearsalSettingsRepository? = null,
    private val clock: RehearsalClock = RehearsalClock.monotonic(),
    private val pollMs: Long = 20L,
    private val operationTimeoutMs: Long = 30_000L,
    private val stalledPlaybackTimeoutMs: Long = 15_000L,
) {
    private val _state = MutableStateFlow(RehearsalState())
    val state: StateFlow<RehearsalState> = _state.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var timerDeadlineMs: Long? = null
    private var resumePlaybackRequested = false
    private var startAttempt: Deferred<Unit>? = null
    private var pauseVersion = 0L

    init {
        require(pollMs > 0L)
        require(operationTimeoutMs > 0L)
        require(stalledPlaybackTimeoutMs > 0L)
    }

    fun start(settings: RehearsalSettings, plan: RehearsalPlan) {
        cancelCurrent()
        val currentGeneration = generation
        val errors = RehearsalValidator.errors(settings, plan.durationMs, plan.revision).toMutableList()
        if (settings.choreographyId != plan.choreographyId) errors += "Выбрана другая постановка. Проверьте настройки."
        _state.value = RehearsalState(totalPasses = settings.totalPasses, positionMs = settings.startMs)
        if (errors.isNotEmpty()) {
            fail(errors.joinToString("\n"))
            return
        }
        // Loading is already an active, pausable phase before any suspend operation.
        // Otherwise Activity.onStop during access/prepare could be ignored and later autoplay.
        _state.update { it.copy(phase = RehearsalPhase.Preparing, isWaitingForPlayer = true) }
        job = scope.launch {
            try {
                val access = withTimeout(operationTimeoutMs) { entitlements.access() }
                checkCurrent(currentGeneration)
                _state.update { it.copy(access = access) }
                if (access != RehearsalAccess.Granted) {
                    _state.update {
                        it.copy(phase = RehearsalPhase.Ready, pausedFrom = null, isWaitingForPlayer = false)
                    }
                    return@launch
                }
                awaitUnpaused(currentGeneration)
                _state.update { it.copy(isWaitingForPlayer = true) }
                val confirmedDuration = withTimeout(operationTimeoutMs) { playback.prepare(plan) }
                checkCurrent(currentGeneration)
                val confirmedErrors = RehearsalValidator.errors(settings, confirmedDuration, plan.revision)
                if (confirmedErrors.isNotEmpty()) {
                    fail(confirmedErrors.joinToString("\n"))
                    return@launch
                }
                if (confirmedDuration != plan.durationMs) {
                    fail("Длительность музыки изменилась. Откройте постановку и проверьте границы участка.")
                    return@launch
                }
                if (settings.voiceEnabled) {
                    awaitUnpaused(currentGeneration)
                    withTimeout(operationTimeoutMs) { voice.prepare() }
                    checkCurrent(currentGeneration)
                    awaitUnpaused(currentGeneration)
                }
                settingsRepository?.write(settings)
                checkCurrent(currentGeneration)
                prime(settings.startMs, currentGeneration)
                awaitUnpaused(currentGeneration)
                waitTimer(RehearsalPhase.Preparing, settings.preparationMs, settings.preparationMs, currentGeneration)
                for (pass in 1..settings.totalPasses) {
                    awaitUnpaused(currentGeneration)
                    startPlayback(currentGeneration)
                    checkCurrent(currentGeneration)
                    _state.update {
                        it.copy(phase = RehearsalPhase.Playing, pass = pass, remainingMs = 0L, isWaitingForPlayer = false)
                    }
                    playSection(settings, plan, currentGeneration)
                    playback.pause()
                    voice.stop()
                    checkCurrent(currentGeneration)
                    if (pass == settings.totalPasses) {
                        playback.stop()
                        _state.update {
                            it.copy(
                                phase = RehearsalPhase.Completed,
                                positionMs = settings.endMs,
                                remainingMs = 0L,
                                isWaitingForPlayer = false,
                                currentLabel = "",
                                activeLabels = emptyList(),
                            )
                        }
                        return@launch
                    }
                    // Seek silently during rest. Buffering beyond P is shown separately and never counts as a pass.
                    coroutineScope {
                        val priming = async { prime(settings.startMs, currentGeneration, showWaiting = false) }
                        waitTimer(RehearsalPhase.Resting, settings.restMs, settings.preparationMs, currentGeneration)
                        checkCurrent(currentGeneration)
                        if (!priming.isCompleted) _state.update { it.copy(isWaitingForPlayer = true) }
                        priming.await()
                        _state.update { it.copy(isWaitingForPlayer = false) }
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                if (currentGeneration == generation) {
                    fail("Не удалось подготовить участок вовремя. Проверьте музыкальные файлы и повторите запуск.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (currentGeneration == generation) fail(error.message ?: "Не удалось воспроизвести участок.")
            }
        }
    }

    fun pause() {
        val current = _state.value
        if (current.phase !in setOf(RehearsalPhase.Preparing, RehearsalPhase.Playing, RehearsalPhase.Resting)) return
        val remaining = timerDeadlineMs?.let { (it - clock.nowMs()).coerceAtLeast(0L) } ?: current.remainingMs
        timerDeadlineMs = null
        _state.value = current.copy(phase = RehearsalPhase.Paused, pausedFrom = current.phase, remainingMs = remaining)
        pauseVersion++
        startAttempt?.cancel()
        playback.pause()
        voice.stop()
    }

    fun resume() {
        val current = _state.value
        if (current.phase != RehearsalPhase.Paused) return
        val previous = current.pausedFrom ?: return
        if (previous == RehearsalPhase.Preparing || previous == RehearsalPhase.Resting) {
            timerDeadlineMs = clock.nowMs() + current.remainingMs
        }
        if (previous == RehearsalPhase.Playing) resumePlaybackRequested = true
        _state.value = current.copy(phase = previous, pausedFrom = null)
        // Running coroutine owns the actual start. No new pass or fresh countdown on resume.
    }

    fun stop() {
        cancelCurrent()
        _state.update {
            it.copy(
                phase = RehearsalPhase.Ready, pausedFrom = null, pass = 0,
                remainingMs = 0L, currentLabel = "", activeLabels = emptyList(),
                isWaitingForPlayer = false, error = null,
            )
        }
    }

    private fun cancelCurrent() {
        generation++
        job?.cancel()
        job = null
        startAttempt?.cancel()
        startAttempt = null
        timerDeadlineMs = null
        resumePlaybackRequested = false
        voice.stop()
        playback.stop()
    }

    private fun fail(message: String) {
        timerDeadlineMs = null
        resumePlaybackRequested = false
        voice.stop()
        playback.stop()
        _state.update {
            it.copy(
                phase = RehearsalPhase.Error, error = message, pausedFrom = null,
                isWaitingForPlayer = false, remainingMs = 0L,
            )
        }
    }

    private suspend fun checkCurrent(expected: Long) {
        coroutineContext.ensureActive()
        if (generation != expected) throw CancellationException("Superseded rehearsal")
    }

    private suspend fun awaitUnpaused(expected: Long) {
        checkCurrent(expected)
        while (_state.value.phase == RehearsalPhase.Paused) {
            delay(pollMs)
            checkCurrent(expected)
        }
    }

    private suspend fun prime(positionMs: Long, expected: Long, showWaiting: Boolean = true) {
        if (showWaiting) _state.update { it.copy(isWaitingForPlayer = true) }
        withTimeout(operationTimeoutMs) {
            playback.seekTo(positionMs)
            checkCurrent(expected)
            playback.awaitReady()
        }
        checkCurrent(expected)
        if (showWaiting) _state.update { it.copy(isWaitingForPlayer = false) }
    }

    private suspend fun startPlayback(expected: Long) {
        while (true) {
            awaitUnpaused(expected)
            val attemptPauseVersion = pauseVersion
            _state.update { it.copy(isWaitingForPlayer = true) }
            // A pause between player.play() and isPlaying must cancel that handshake,
            // not leave it waiting for an event which pause deliberately prevented.
            val started = coroutineScope {
                val attempt = async {
                    withTimeout(operationTimeoutMs) { playback.playAndAwaitStarted() }
                }
                startAttempt = attempt
                try {
                    attempt.await()
                    true
                } catch (cancelled: CancellationException) {
                    coroutineContext.ensureActive()
                    if (pauseVersion != attemptPauseVersion) false else throw cancelled
                } finally {
                    if (startAttempt === attempt) startAttempt = null
                }
            }
            checkCurrent(expected)
            if (started && _state.value.phase != RehearsalPhase.Paused && pauseVersion == attemptPauseVersion) {
                resumePlaybackRequested = false
                return
            }
            playback.pause()
        }
    }

    private suspend fun waitTimer(phase: RehearsalPhase, durationMs: Long, countdownMs: Long, expected: Long) {
        checkCurrent(expected)
        if (durationMs <= 0L) return
        _state.update {
            it.copy(phase = phase, remainingMs = durationMs, currentLabel = "", activeLabels = emptyList())
        }
        timerDeadlineMs = clock.nowMs() + durationMs
        var lastBeep = -1
        val beepWindow = minOf(durationMs, countdownMs)
        while (true) {
            awaitUnpaused(expected)
            val remaining = ((timerDeadlineMs ?: clock.nowMs()) - clock.nowMs()).coerceAtLeast(0L)
            _state.update { it.copy(remainingMs = remaining) }
            if (remaining <= 0L) break
            val seconds = ((remaining + 999L) / 1_000L).toInt()
            if (beepWindow > 0L && remaining <= beepWindow && seconds != lastBeep) {
                voice.countdown(seconds)
                lastBeep = seconds
            }
            delay(minOf(pollMs, remaining))
        }
        timerDeadlineMs = null
    }

    private suspend fun playSection(settings: RehearsalSettings, plan: RehearsalPlan, expected: Long) {
        val cues = plan.cues(settings)
        var nextCue = 0
        var lastPosition = Long.MIN_VALUE
        var lastProgressAt = clock.nowMs()
        while (true) {
            checkCurrent(expected)
            if (_state.value.phase == RehearsalPhase.Paused || resumePlaybackRequested) {
                awaitUnpaused(expected)
                startPlayback(expected)
                resumePlaybackRequested = false
                _state.update { it.copy(isWaitingForPlayer = false) }
                lastProgressAt = clock.nowMs()
            }
            val position = playback.positionMs()
            // Test boundary BEFORE dispatching cues: nothing at or beyond B may be spoken.
            if (position >= settings.endMs) return
            if (position != lastPosition) {
                lastPosition = position
                lastProgressAt = clock.nowMs()
            } else if (clock.nowMs() - lastProgressAt >= stalledPlaybackTimeoutMs) {
                throw IllegalStateException("Музыка остановилась. Проверьте файл и повторите запуск участка.")
            }
            val labels = plan.visualElementsAt(position).map { it.label }
            _state.update {
                it.copy(positionMs = position, currentLabel = labels.lastOrNull().orEmpty(), activeLabels = labels)
            }
            var due: RehearsalCue? = null
            val elapsed = position - settings.startMs
            while (nextCue < cues.size && cues[nextCue].offsetMs <= elapsed) {
                due = cues[nextCue++]
            }
            if (settings.voiceEnabled && due != null) voice.speak(due.label)
            delay(pollMs)
        }
    }
}
