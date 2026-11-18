package com.midnightdancer.feature.choreography.logic

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** C-275: Add-element picker must hide rows without video. */
class ChoreoCatalogVideoOnlyTest {

    @Test
    fun elementsForStyle_hidesBlankMediaUri() {
        ChoreoCatalog.replaceFromAppData(
            AppData(
                danceStyles = listOf(
                    DanceStyle(
                        id = "s1",
                        name = "Bachata",
                        moves = listOf(
                            Move(
                                id = "m1",
                                name = "With video",
                                level = "База",
                                description = "",
                                videoUri = "file://a.mp4",
                            ),
                            Move(
                                id = "m2",
                                name = "No video",
                                level = "База",
                                description = "",
                                videoUri = "",
                            ),
                            Move(
                                id = "m3",
                                name = "Null video",
                                level = "База",
                                description = "",
                                videoUri = null,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val rows = ChoreoCatalog.elementsForStyle("Bachata")
        assertEquals(listOf("With video"), rows.map { it.title })
        assertTrue(rows.all { !it.mediaUri.isNullOrBlank() })
    }
}
