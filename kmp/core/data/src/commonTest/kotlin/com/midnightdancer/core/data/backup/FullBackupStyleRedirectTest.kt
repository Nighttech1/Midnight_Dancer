package com.midnightdancer.core.data.backup

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.exchange.StyleImportPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FullBackupStyleRedirectTest {

    @Test
    fun folds_package_style_moves_into_local_style() {
        val local =
            AppData(
                danceStyles =
                    listOf(
                        DanceStyle(id = "sensual", name = "Sensual", moves = emptyList()),
                    ),
            )
        val imported =
            AppData(
                danceStyles =
                    listOf(
                        DanceStyle(
                            id = "pack-bachata",
                            name = "Bachata",
                            moves =
                                listOf(
                                    Move(id = "m1", name = "Wave", level = "beginner"),
                                ),
                        ),
                    ),
            )
        val out =
            FullBackupStyleRedirect.apply(
                imported = imported,
                local = local,
                plan = StyleImportPlan(mapOf("pack-bachata" to "sensual")),
            )
        assertEquals(1, out.danceStyles.size)
        assertEquals("sensual", out.danceStyles.single().id)
        assertTrue(out.danceStyles.single().moves.any { it.id == "m1" })
    }
}
