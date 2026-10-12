package com.midnightdancer.core.data.backup

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.entity.Song

/**
 * Strip dangerous media refs from imported catalog before merge (S-07).
 *
 * - [Move.videoUri]: null out absolute / `content:` / `file:` / `http(s):` / traversal;
 *   keep relative basename only when layout-safe.
 * - [Song.fileName]: relative basename only (no schemes / absolute paths).
 */
object ImportMediaRefSanitizer {
    fun sanitizeImportedAppData(imported: AppData): AppData =
        imported.copy(
            danceStyles = imported.danceStyles.map { sanitizeStyle(it) },
            songs = imported.songs.map { sanitizeSong(it) },
        )

    fun sanitizeVideoUri(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        val t = uri.trim()
        if (hasDangerousSchemeOrAbsolute(t)) return null
        if (containsTraversal(t)) return null
        val base = t.replace('\\', '/').substringAfterLast('/')
        if (base.isBlank() || hasDangerousSchemeOrAbsolute(base) || containsTraversal(base)) {
            return null
        }
        // layout-safe: basename without separators / schemes
        if ('/' in base || '\\' in base || ':' in base) return null
        return base
    }

    fun sanitizeFileName(fileName: String): String {
        val t = fileName.trim()
        if (t.isEmpty()) return "imported"
        if (hasDangerousSchemeOrAbsolute(t) || containsTraversal(t)) {
            val base = t.replace('\\', '/').substringAfterLast('/')
            val cleaned = base.substringAfterLast(':').trim()
            return cleaned.ifBlank { "imported" }.let { name ->
                if ('/' in name || '\\' in name || containsTraversal(name)) "imported" else name
            }
        }
        val base = t.replace('\\', '/').substringAfterLast('/')
        return base.ifBlank { "imported" }
    }

    private fun sanitizeStyle(style: DanceStyle): DanceStyle =
        style.copy(moves = style.moves.map { sanitizeMove(it) })

    private fun sanitizeMove(move: Move): Move =
        move.copy(videoUri = sanitizeVideoUri(move.videoUri))

    private fun sanitizeSong(song: Song): Song =
        song.copy(fileName = sanitizeFileName(song.fileName))

    private fun hasDangerousSchemeOrAbsolute(value: String): Boolean {
        val lower = value.lowercase()
        if (lower.startsWith("content:") ||
            lower.startsWith("file:") ||
            lower.startsWith("http:") ||
            lower.startsWith("https:")
        ) {
            return true
        }
        if (value.startsWith("/")) return true
        // Windows drive / UNC-ish
        if (value.length >= 2 && value[1] == ':') return true
        return false
    }

    private fun containsTraversal(value: String): Boolean {
        val norm = value.replace('\\', '/')
        if (".." in norm.split('/')) return true
        return false
    }
}