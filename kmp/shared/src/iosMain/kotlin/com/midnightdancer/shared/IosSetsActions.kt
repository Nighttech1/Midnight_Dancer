package com.midnightdancer.shared

import com.midnightdancer.core.data.db.RoomCatalogRepository
import com.midnightdancer.core.domain.exchange.CatalogPickItem
import com.midnightdancer.core.domain.exchange.ExportCatalogSnapshot
import com.midnightdancer.core.domain.exchange.ExportPickKind
import com.midnightdancer.core.domain.exchange.StyleRef
import com.midnightdancer.core.domain.styles.ManageDanceStyles
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultPersister
import com.midnightdancer.feature.choreography.logic.decodeVaultItems
import com.midnightdancer.feature.exchange.logic.ExchangeComponent
import com.midnightdancer.feature.exchange.logic.ExchangeIntent
import com.midnightdancer.feature.exchange.logic.LocalSetCatalogLoader
import com.midnightdancer.feature.exchange.logic.SetHubTab
import com.midnightdancer.feature.exchange.logic.SetsSortMode

internal class IosLocalSetCatalogLoader(
    private val library: RoomCatalogRepository,
    private val vault: ChoreographyVaultPersister,
    private val styles: ManageDanceStyles,
) : LocalSetCatalogLoader {
    override suspend fun load(): ExportCatalogSnapshot {
        val data = library.load()
        val catalog = checkNotNull(styles.state.value)
        return ExportCatalogSnapshot(
            styles = catalog.entries.map { StyleRef(it.id, it.name) },
            elements = data.danceStyles.flatMap { style ->
                style.moves.map { move ->
                    CatalogPickItem(move.id, move.name, style.name, catalog.canonicalId(style.id))
                }
            },
            songs = data.songs.map { song ->
                CatalogPickItem(
                    song.id,
                    song.title,
                    styleId = catalog.canonicalId(song.danceStyle),
                    approximateBytes = song.sizeBytes.toLong().coerceAtLeast(0L),
                )
            },
            choreographies = vault.readRaw()?.let(::decodeVaultItems).orEmpty().map { item ->
                CatalogPickItem(item.id, item.title, item.durationLabel, catalog.canonicalId(item.style))
            },
        )
    }
}

/** UI actions reuse the Android set reducer. Archive sharing/import is a separate platform port. */
object IosSetsActions {
    fun selectTab(component: ExchangeComponent, imported: Boolean) =
        component.onIntent(ExchangeIntent.SelectSetHubTab(if (imported) SetHubTab.Imported else SetHubTab.Mine))

    fun search(component: ExchangeComponent, query: String) =
        component.onIntent(ExchangeIntent.SetSetsSearchQuery(query))

    fun filter(component: ExchangeComponent, tag: String?) =
        component.onIntent(ExchangeIntent.SetSetsFilter(tag))

    fun sort(component: ExchangeComponent, alphabetical: Boolean) =
        component.onIntent(ExchangeIntent.SetSetsSortMode(if (alphabetical) SetsSortMode.Alphabetical else SetsSortMode.ByStyle))

    fun open(component: ExchangeComponent, id: String) =
        component.onIntent(ExchangeIntent.EditSavedSet(id))

    fun delete(component: ExchangeComponent, id: String) =
        component.onIntent(ExchangeIntent.DeleteSavedSet(id))

    fun name(component: ExchangeComponent, value: String) =
        component.onIntent(ExchangeIntent.SetExportSetName(value))

    fun openSection(component: ExchangeComponent, kind: ExportPickKind) =
        component.onIntent(ExchangeIntent.OpenExportPick(kind))

    fun closeSection(component: ExchangeComponent) =
        component.onIntent(ExchangeIntent.DismissExportPick)

    fun toggle(component: ExchangeComponent, id: String) =
        component.onIntent(ExchangeIntent.ToggleExportPickItem(id))

    fun apply(component: ExchangeComponent) =
        component.onIntent(ExchangeIntent.ConfirmExportPick)

    fun save(component: ExchangeComponent) =
        component.onIntent(ExchangeIntent.ConfirmLocalSet)

    fun retryArchive(component: ExchangeComponent) =
        component.onIntent(ExchangeIntent.RetryArchiveInspection)

    fun removeInvalid(component: ExchangeComponent) =
        component.onIntent(ExchangeIntent.RemoveInvalidExportSelection)
}
