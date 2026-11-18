package com.midnightdancer.core.domain.audio

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Cross-feature signal: Trainer Live / Pro Editor play must stop library music (C-178).
 */
object LibraryAudioStopBus {
    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    fun requestStop() {
        _requests.tryEmit(Unit)
    }
}
