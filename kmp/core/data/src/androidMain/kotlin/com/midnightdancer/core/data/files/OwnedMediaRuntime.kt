package com.midnightdancer.core.data.files

import com.midnightdancer.core.domain.media.*
import java.util.concurrent.atomic.AtomicReference

/** Fail-closed process resolver. Application initializes aliases before binding or warming players. */
object OwnedMediaRuntime {
    private data class State(val store: OwnedMediaStore? = null, val failure: OwnedMediaException? = null)
    private val state = AtomicReference(State())
    /** Caller must await initialize(); a failed initialization must call bindFailure instead. */
    fun bind(initializedStore: OwnedMediaStore) { state.set(State(store = initializedStore)) }
    fun bindFailure(failure: OwnedMediaException) { state.set(State(failure = failure)) }
    fun resolveReference(reference: String): String {
        val current = state.get()
        current.failure?.let { throw it }
        val store = current.store ?: throw OwnedMediaException(OwnedMediaFailure.Unavailable)
        return store.resolveReference(reference)
    }
}
