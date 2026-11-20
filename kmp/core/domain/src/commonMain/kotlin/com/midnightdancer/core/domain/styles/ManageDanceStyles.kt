package com.midnightdancer.core.domain.styles

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

object ReservedDanceStyle {
    const val ID = "md-style-unstyled"
    const val NAME = "Без стиля"
}

@Serializable
data class DanceStyleEntry(val id: String, val name: String, val reserved: Boolean = false)

@Serializable
data class DanceStyleCatalogSnapshot(
    val revision: Long = 0,
    val entries: List<DanceStyleEntry> = emptyList(),
    val redirects: Map<String, String> = emptyMap(),
    val legacyAliases: Map<String, String> = emptyMap(),
) {
    fun resolveId(value: String): String? {
        entries.firstOrNull { it.id == value }?.let { return it.id }
        redirects[value]?.let { return it }
        legacyAliases[value.trim().lowercase()]?.let { return redirects[it] ?: it }
        return entries.filter { it.name.equals(value.trim(), true) }.singleOrNull()?.id
    }

    fun canonicalId(value: String): String = resolveId(value) ?: ReservedDanceStyle.ID
    /** A fresh explicit picker choice takes precedence over a historical name tombstone. */
    fun choiceId(value: String): String =
        entries.firstOrNull { it.id == value }?.id
            ?: entries.singleOrNull { it.name.equals(value.trim(), true) }?.id
            ?: canonicalId(value)
    fun displayName(value: String): String =
        entries.firstOrNull { it.id == canonicalId(value) }?.name ?: ReservedDanceStyle.NAME
}

interface ManageDanceStyles {
    val state: StateFlow<DanceStyleCatalogSnapshot?>
    /** Legacy names are consumed once, after the platform catalog migration. */
    suspend fun bootstrap(legacyNames: List<String> = emptyList())
    suspend fun add(name: String)
    suspend fun rename(id: String, name: String)
    suspend fun remove(id: String)
}
