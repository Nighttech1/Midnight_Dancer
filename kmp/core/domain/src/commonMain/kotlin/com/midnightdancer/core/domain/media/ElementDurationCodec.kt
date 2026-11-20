package com.midnightdancer.core.domain.media

/**
 * Stores video duration inside [com.midnightdancer.core.domain.entity.Move.description]
 * without a Room schema bump (C-189). Prefix is stripped for UI.
 */
object ElementDurationCodec {
    private val PREFIX = Regex("""^\[md_dur=(\d+)]""")

    fun durationMs(description: String?): Long =
        PREFIX.find(description.orEmpty())?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0L } ?: 0L

    fun userText(description: String?): String =
        description.orEmpty().replace(PREFIX, "")

    fun pack(userDescription: String, durationMs: Long): String {
        val body = userText(userDescription)
        return if (durationMs > 0L) "[md_dur=$durationMs]$body" else body
    }

    fun formatLabel(durationMs: Long): String {
        if (durationMs <= 0L) return "—"
        val totalSec = (durationMs / 1000L).toInt().coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }
}
