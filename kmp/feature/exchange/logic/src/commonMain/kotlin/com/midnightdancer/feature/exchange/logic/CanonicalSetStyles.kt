package com.midnightdancer.feature.exchange.logic

import com.midnightdancer.core.domain.styles.DanceStyleCatalogSnapshot

fun normalizeSavedSetStyles(sets: List<SavedSet>, catalog: DanceStyleCatalogSnapshot): List<SavedSet> =
    sets.map { set ->
        if (set.origin == SavedSetOrigin.Imported || set.styleId.isBlank()) set
        else {
            val id = catalog.resolveId(set.styleId) ?: catalog.canonicalId(set.styleName)
            set.copy(styleId = id, styleName = catalog.displayName(id))
        }
    }

fun com.midnightdancer.core.domain.exchange.ExportCatalogSnapshot.normalizeLocalCatalog(catalog: DanceStyleCatalogSnapshot) = copy(
    styles = catalog.entries.map { com.midnightdancer.core.domain.exchange.StyleRef(it.id, it.name) },
    elements = elements.map { it.copy(styleId = catalog.canonicalId(it.styleId), subtitle = catalog.displayName(it.styleId)) },
    songs = songs.map { it.copy(styleId = catalog.canonicalId(it.styleId), subtitle = catalog.displayName(it.styleId)) },
    choreographies = choreographies.map { it.copy(styleId = catalog.canonicalId(it.styleId), subtitle = catalog.displayName(it.styleId)) },
)
