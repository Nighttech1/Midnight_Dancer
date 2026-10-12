package com.midnightdancer.core.domain.timeline

import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move

/**
 * Pure timeline ref helpers — Flutter `ChoreographyTimelineRef`.
 * New format: `styleId::moveId`; legacy: bare move name or id.
 */
object ChoreographyTimelineRef {
    const val SEP: String = "::"

    data class Parts(val styleId: String, val moveId: String)

    fun encode(styleId: String, moveId: String): String = "$styleId$SEP$moveId"

    fun decode(value: String): Parts? {
        val i = value.indexOf(SEP)
        if (i <= 0 || i + SEP.length >= value.length) return null
        return Parts(
            styleId = value.substring(0, i),
            moveId = value.substring(i + SEP.length),
        )
    }

    fun resolveMove(
        styles: List<DanceStyle>,
        choreographyStyleId: String,
        value: String,
    ): Move? {
        if (value.isEmpty()) return null
        val dec = decode(value)
        if (dec != null) {
            for (s in styles) {
                if (s.id != dec.styleId) continue
                for (m in s.moves) {
                    if (m.id == dec.moveId) return m
                }
            }
            return null
        }
        for (s in styles) {
            if (s.id != choreographyStyleId) continue
            for (m in s.moves) {
                if (m.name == value || m.id == value) return m
            }
        }
        for (s in styles) {
            for (m in s.moves) {
                if (m.name == value || m.id == value) return m
            }
        }
        return null
    }

    /** Timeline entry is valid when non-blank and (encoded) parts are non-empty ids. */
    fun isValidRef(value: String): Boolean {
        if (value.isBlank()) return false
        val dec = decode(value)
        if (dec != null) return dec.styleId.isNotBlank() && dec.moveId.isNotBlank()
        // Contains separator but failed decode → malformed encoded ref (e.g. "::", "a::").
        if (value.contains(SEP)) return false
        return true // legacy bare name/id
    }
}
