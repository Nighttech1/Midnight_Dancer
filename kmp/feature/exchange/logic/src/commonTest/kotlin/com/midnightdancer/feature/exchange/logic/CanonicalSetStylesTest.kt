package com.midnightdancer.feature.exchange.logic

import com.midnightdancer.core.domain.styles.*
import kotlin.test.*

class CanonicalSetStylesTest {
    @Test fun mineMovesToReservedButImportedHistoryAndCreationDraftAreExact() {
        val catalog = DanceStyleCatalogSnapshot(2,
            listOf(DanceStyleEntry(ReservedDanceStyle.ID, ReservedDanceStyle.NAME, true)),
            mapOf("a" to ReservedDanceStyle.ID))
        val mine = SavedSet("mine", "Name", "a", "Alpha", moveIds = listOf("m"), songIds = listOf("s"),
            choreoIds = listOf("c"), archivePath = "/owned/mine.dance", tags = listOf("tag"))
        val imported = mine.copy(id = "imported", origin = SavedSetOrigin.Imported, archivePath = "/owned/history.dance")
        val draft = SavedSet("draft", "", "")
        val result = normalizeSavedSetStyles(listOf(mine, imported, draft), catalog)
        assertEquals(mine.copy(styleId = ReservedDanceStyle.ID, styleName = ReservedDanceStyle.NAME), result[0])
        assertSame(imported, result[1])
        assertSame(draft, result[2])
        assertEquals(result, normalizeSavedSetStyles(result, catalog))
    }
}
