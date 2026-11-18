package com.midnightdancer.feature.exchange.logic

/** Durable metadata and immutable imported archives. Empty list means an intentionally empty library. */
interface SavedSetsPersister {
    /** Null only when no metadata file has been written yet. */
    fun read(): List<SavedSet>?

    fun write(sets: List<SavedSet>)
    /** Platform may project raw metadata without rebuilding historical card fields or collecting archives. */
    fun refreshStyles() { read()?.let(::write) }

    /** Copy the source into owned storage; the inbox/export source may be replaced by a later operation. */
    fun retainArchive(sourcePath: String, setId: String): String
}
