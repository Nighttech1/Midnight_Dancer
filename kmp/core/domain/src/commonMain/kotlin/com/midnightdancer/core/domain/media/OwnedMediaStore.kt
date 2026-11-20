package com.midnightdancer.core.domain.media

/** Bytes are owned once; clips, trims and copies retain only this reference. */
enum class OwnedMediaKind { Video, Audio }
enum class OwnedMediaPhase { Reading, Publishing, Ready }
data class OwnedMediaProgress(val copiedBytes: Long, val totalBytes: Long? = null, val phase: OwnedMediaPhase = OwnedMediaPhase.Reading)
data class OwnedMedia(val reference: String, val sha256: String, val bytes: Long, val reused: Boolean)
data class OwnedMediaStats(val poolFiles: Int, val poolBytes: Long, val aliasCount: Int, val freeBytes: Long)
enum class OwnedMediaFailure { Unavailable, NoSpace, Unsupported, CorruptOwned, IO }
class OwnedMediaException(val reason: OwnedMediaFailure, cause: Throwable? = null) : Exception(reason.name, cause)

interface OwnedMediaStore {
    /** Load the durable alias snapshot off Main before binding a resolver or starting players. */
    suspend fun initialize()
    /** Reads the current explicit source, bypassing aliases; protects old raw references with a first-write-wins
     * alias before success, while returning this pick's current version. Never deletes the source. */
    suspend fun retain(reference: String, kind: OwnedMediaKind, onProgress: ((OwnedMediaProgress) -> Unit)? = null): OwnedMedia
    /** First successful adoption wins; a reused external URI must never retarget old snapshots. */
    suspend fun adopt(reference: String, kind: OwnedMediaKind, onProgress: ((OwnedMediaProgress) -> Unit)? = null): OwnedMedia
    /** Pure cached lookup. A missing mapped blob stays mapped; never falls back to the external URI. */
    fun resolveReference(reference: String): String
    /** Filesystem availability check; invoke off Main. Cache files are never durable ownership. */
    fun isDurableOwnedReference(reference: String): Boolean
    /** Only this pool, excluding source originals, archives and preview caches. */
    suspend fun inspect(): OwnedMediaStats
}
