package com.midnightdancer.feature.choreography.logic

internal const val FREE_CREATION_MESSAGE = "В Free доступна одна собственная постановка. Безлимит — в Pro. Все сохранённые постановки остаются доступны."

/** Demo seeds must not consume the first Free creation; unknown legacy IDs remain counted. */
fun ownChoreographyCount(items: List<ChoreographyVaultItem>): Int {
    val demoIds = DEFAULT_VAULT_ITEMS.map { it.id }.toSet()
    return items.count { it.snapshot != null || it.id !in demoIds }
}
