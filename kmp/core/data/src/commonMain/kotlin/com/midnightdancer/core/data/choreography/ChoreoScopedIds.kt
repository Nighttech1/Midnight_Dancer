package com.midnightdancer.core.data.choreography

/**
 * Ghost style / song ids for choreography-package assets that must NOT appear
 * in Elements / Music catalogs (C-113). Editor + trainer still resolve by name.
 */
object ChoreoScopedIds {
    const val GHOST_STYLE_ID: String = "__md_choreo_scoped__"
    const val GHOST_STYLE_NAME: String = "ChoreoScoped"

    fun isGhostStyle(id: String): Boolean = id == GHOST_STYLE_ID

    fun isScopedSongId(id: String): Boolean = id.startsWith("choreo-scoped-")

    fun scopedSongId(originalId: String): String =
        if (isScopedSongId(originalId)) originalId else "choreo-scoped-$originalId"
}
