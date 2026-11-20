package com.midnightdancer.feature.choreography.logic

import com.midnightdancer.core.domain.styles.DanceStyleCatalogSnapshot

fun normalizeVaultStyles(items: List<ChoreographyVaultItem>, catalog: DanceStyleCatalogSnapshot): List<ChoreographyVaultItem> =
    items.map { item -> item.copy(style = catalog.canonicalId(item.style),
        snapshot = item.snapshot?.let { it.copy(style = catalog.canonicalId(it.style)) }) }
