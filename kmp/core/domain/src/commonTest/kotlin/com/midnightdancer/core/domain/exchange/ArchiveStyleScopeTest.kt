package com.midnightdancer.core.domain.exchange

import kotlin.test.*

class ArchiveStyleScopeTest {
    @Test fun blank_unknown_and_alias_styles_use_same_fail_closed_scope_for_all_three_sections() {
        val rows = listOf(CatalogPickItem("s-id", "ID", styleId = "s"),
            CatalogPickItem("s-alias", "Alias", styleId = "Sensual"),
            CatalogPickItem("foreign", "Foreign", styleId = "b"), CatalogPickItem("blank", "Blank"))
        val catalog = ExportCatalogSnapshot(listOf(StyleRef("s", "Sensual"), StyleRef("b", "Bachata")), rows, rows, rows)
        for (scope in listOf<(String) -> List<CatalogPickItem>>(catalog::elementsForStyle, catalog::songsForStyle, catalog::choreosForStyle)) {
            assertTrue(scope("").isEmpty()); assertTrue(scope("missing").isEmpty())
            assertEquals(listOf("s-id", "s-alias"), scope("s").map { it.id })
            assertEquals(listOf("s-id", "s-alias"), scope("SENSUAL").map { it.id })
        }
    }
    @Test fun real_style_id_wins_over_another_styles_display_alias_without_merging_scopes() {
        val rows = listOf(CatalogPickItem("one", "One", styleId = "s"), CatalogPickItem("two", "Two", styleId = "b"))
        val catalog = ExportCatalogSnapshot(listOf(StyleRef("s", "b"), StyleRef("b", "Bachata")), rows, rows, rows)
        assertEquals(listOf("two"), catalog.elementsForStyle("b").map { it.id })
        assertEquals(listOf("one"), catalog.elementsForStyle("s").map { it.id })
    }
}
