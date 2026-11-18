package com.midnightdancer.core.domain.debug

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A library track was removed from Music (C-132 / 2.8). */
data class DeletedTrackEvent(
    val id: String,
    val title: String,
)

/**
 * Process-wide soft signal: Music → Choreography.
 * Choreography hub / Pro Editor drop stub labels that match a deleted track title
 * (until Room-backed `songId` refs land).
 */
object DeletedTrackBus {
    private val _events = MutableSharedFlow<DeletedTrackEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<DeletedTrackEvent> = _events.asSharedFlow()

    fun publish(id: String, title: String) {
        _events.tryEmit(DeletedTrackEvent(id = id, title = title))
    }
}
