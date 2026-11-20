package com.midnightdancer.app.ui.trainer

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.midnightdancer.app.access.ProductAccessRuntime
import com.midnightdancer.app.audio.AndroidRehearsalPlayback
import com.midnightdancer.app.audio.AndroidRehearsalVoice
import com.midnightdancer.core.tts.TrainerVoice
import com.midnightdancer.core.tts.createPlatformTtsEngine
import com.midnightdancer.feature.trainer.logic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class RehearsalRuntimeKey(val id: String, val result: RehearsalPlanResult, val voice: TrainerVoice)

/** One selected session belongs to the Activity, not a recyclable pager/list composition. */
internal class RetainedRehearsalViewModel(
    context: Context,
    private val repository: RehearsalSettingsRepository = AndroidRehearsalSettingsRepository(context.applicationContext),
) : ViewModel() {
    private val application = context.applicationContext
    private var current: RetainedRehearsalSession? = null

    fun obtain(key: RehearsalRuntimeKey): RetainedRehearsalSession {
        current?.takeIf { it.key == key }?.let { return it }
        current?.close()
        return RetainedRehearsalSession(application, key, repository).also { current = it }
    }

    override fun onCleared() { current?.close(); current = null }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == RetainedRehearsalViewModel::class.java)
            return RetainedRehearsalViewModel(context.applicationContext) as T
        }
    }
}

internal class RetainedRehearsalSession(
    context: Context,
    val key: RehearsalRuntimeKey,
    repository: RehearsalSettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playback = AndroidRehearsalPlayback(context.applicationContext)
    private val voice = AndroidRehearsalVoice(scope, createPlatformTtsEngine(), key.voice)
    private val failure = MutableStateFlow<String?>(null)
    val voiceFailure = failure.asStateFlow()
    val controller = RehearsalController(scope, playback, voice, RehearsalEntitlementPort {
        if (ProductAccessRuntime.state.value.canRehearse) RehearsalAccess.Granted else RehearsalAccess.Unavailable
    }, repository)
    private var closed = false

    init {
        playback.onInterrupted = controller::pause
        voice.onFailure = { reason ->
            controller.stop()
            failure.value = "Не удалось озвучить подсказку: $reason"
        }
    }

    var configuration: RehearsalSettings? = null
        private set

    fun start(settings: RehearsalSettings, plan: RehearsalPlan) {
        if (closed) return
        // Repository write can await media/voice readiness: UI must retain this exact draft meanwhile.
        configuration = settings
        controller.start(settings, plan)
    }
    fun clearConfiguration() { configuration = null }
    fun clearFailure() { failure.value = null }
    fun pauseForUiLeave() { if (!closed) controller.pause() }
    fun close() {
        if (closed) return
        closed = true
        // Voice release performs an immediate engine stop before its child scope is cancelled.
        try { controller.stop() } finally {
            try { voice.release() } finally {
                try { playback.release() } finally { scope.cancel() }
            }
        }
    }
}

internal tailrec fun Context.rehearsalActivity(): ComponentActivity = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.rehearsalActivity()
    else -> error("Rehearsal requires an Activity ViewModel owner")
}
