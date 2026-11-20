package com.midnightdancer.feature.choreography.logic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * C-273: process-scoped vault list so Trainer can pick a choreography even when
 * the Choreography tab ChildPages instance was recreated.
 */
object ChoreographyVaultStore {
    private val _items = MutableStateFlow<List<ChoreographyVaultItem>>(emptyList())
    val items: StateFlow<List<ChoreographyVaultItem>> = _items.asStateFlow()

    fun replaceAll(next: List<ChoreographyVaultItem>) {
        _items.value = next
    }

    fun all(): List<ChoreographyVaultItem> = _items.value

    fun byId(id: String): ChoreographyVaultItem? =
        _items.value.firstOrNull { it.id == id }

    fun titlesForTag(tag: String): List<ChoreographyVaultItem> {
        val t = tag.trim()
        if (t.isEmpty() ||
            t.equals("Все теги", ignoreCase = true) ||
            t.equals("Все", ignoreCase = true)
        ) {
            return _items.value
        }
        return _items.value.filter { item ->
            item.tags.any { it.equals(t, ignoreCase = true) } ||
                item.snapshot?.tag.equals(t, ignoreCase = true)
        }
    }
}
