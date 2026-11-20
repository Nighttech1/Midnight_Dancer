package com.midnightdancer.feature.exchange.logic

import kotlin.concurrent.Volatile

/**
 * Process-scoped SavedSet list so Sets survive ChildPages DESTROY when leaving the tab (C-244).
 * Not a full Room migration — same process lifetime as LibraryAudioController pattern.
 */
object SavedSetsStore {
    @Volatile
    private var sets: List<SavedSet> = emptyList()

    @Volatile
    private var seq: Long = 0L

    fun snapshot(): List<SavedSet> = sets

    fun replaceAll(next: List<SavedSet>) {
        sets = next
    }

    fun currentSeq(): Long = seq

    fun syncSeqFrom(ids: List<String>) {
        val max =
            ids.mapNotNull { id ->
                // ids look like "set-3-42" or "imported-3-42"
                id.split('-').getOrNull(1)?.toLongOrNull()
            }.maxOrNull() ?: 0L
        if (max > seq) seq = max
    }
}
