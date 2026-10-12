package com.midnightdancer.core.data.backup

import com.midnightdancer.core.data.choreography.ChoreographyImportMerge
import com.midnightdancer.core.data.zip.DefaultImportSecureZip
import com.midnightdancer.core.data.zip.DefaultValidateSecureZip
import com.midnightdancer.core.data.zip.FakeZipArchiveInspector
import com.midnightdancer.core.data.zip.MapSecureZipMetadataReader
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.Choreography
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.entity.Song
import com.midnightdancer.core.domain.serialization.DomainJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FullBackupImportMergeTest {
    private val local =
        AppData(
            danceStyles =
                listOf(
                    DanceStyle(
                        id = "st1",
                        name = "Salsa",
                        moves =
                            listOf(
                                Move(id = "m1", name = "Basic", level = "beginner", videoUri = "m1.mp4"),
                            ),
                    ),
                ),
            songs = listOf(Song(id = "s1", title = "A", danceStyle = "Salsa", level = "b", fileName = "a.mp3", duration = 1.0, sizeBytes = 1)),
            choreographies =
                listOf(
                    Choreography(id = "c1", name = "R1", songId = "s1", styleId = "st1", endTime = 10.0),
                ),
            settings = JsonObject(mapOf("ui_language" to JsonPrimitive("ru"))),
        )

    @Test
    fun merges_new_moves_songs_styles_without_overwriting() {
        val imported =
            AppData(
                danceStyles =
                    listOf(
                        DanceStyle(
                            id = "st1",
                            name = "Salsa",
                            moves =
                                listOf(
                                    Move(id = "m1", name = "Basic", level = "beginner"),
                                    Move(id = "m2", name = "Turn", level = "int"),
                                ),
                        ),
                        DanceStyle(id = "st2", name = "Bachata", moves = emptyList()),
                    ),
                songs =
                    listOf(
                        Song(id = "s1", title = "A", danceStyle = "Salsa", level = "b", fileName = "a.mp3", duration = 1.0, sizeBytes = 1),
                        Song(id = "s2", title = "B", danceStyle = "Salsa", level = "b", fileName = "b.mp3", duration = 2.0, sizeBytes = 2),
                    ),
                choreographies =
                    listOf(
                        Choreography(id = "c1", name = "dup", songId = "s1", styleId = "st1", endTime = 5.0),
                        Choreography(id = "c2", name = "new", songId = "s2", styleId = "st1", endTime = 8.0),
                    ),
                settings = JsonObject(mapOf("ui_language" to JsonPrimitive("en"), "extra" to JsonPrimitive("1"))),
            )

        val plan =
            FullBackupImportMerge.mergeInto(
                local = local,
                imported = imported,
                choreographyIdFactory = { "fixed" },
            )

        assertEquals(2, plan.merged.danceStyles.size)
        assertEquals(2, plan.merged.danceStyles.first { it.id == "st1" }.moves.size)
        assertEquals(2, plan.merged.songs.size)
        // local c1 kept + imported c1 renamed + new c2
        assertEquals(3, plan.merged.choreographies.size)
        assertTrue(plan.merged.choreographies.any { it.id == "c1" })
        assertTrue(plan.merged.choreographies.any { it.id.startsWith("c1-import-") })
        assertTrue(plan.merged.choreographies.any { it.id == "c2" })
        assertEquals("ru", (plan.merged.settings["ui_language"] as JsonPrimitive).content)
        assertEquals("1", (plan.merged.settings["extra"] as JsonPrimitive).content)
        assertTrue("m2" in plan.videoMoveIdsToWrite)
        assertTrue("s2" in plan.musicSongIdsToWrite)
        assertTrue("st2" in plan.manifest.wholeStyleIds)
        assertTrue("m2" in plan.manifest.moveIds)
    }

    @Test
    fun defaultMergeImport_embeds_manifest() =
        runTest {
            val merge = DefaultMergeImport()
            val imported =
                AppData(
                    danceStyles = listOf(DanceStyle(id = "stN", name = "New", moves = emptyList())),
                )
            val result = merge.mergeFullBackup(local, imported)
            assertTrue(result.manifest.wholeStyleIds.contains("stN"))
            assertTrue(result.merged.settings.containsKey("last_import_manifest"))
        }
}

class ChoreographyImportMergeTest {
    @Test
    fun unique_names_and_timeline_remap() {
        val existing = listOf(Move(id = "m1", name = "Basic", level = "b"))
        val payload = listOf(Move(id = "x", name = "Basic", level = "b"), Move(id = "y", name = "Spin", level = "i"))
        val r = ChoreographyImportMerge.mergeMoves(existing, payload) { "n$it" }
        assertEquals("Basic (2)", r.imported[0].name)
        assertEquals("Spin", r.imported[1].name)
        assertEquals("n1", r.imported[0].id)
        val tl = mapOf(0.0 to "Basic", 1.0 to "Spin")
        val remapped = ChoreographyImportMerge.remapTimeline(tl, r.renameFromOriginal)
        assertEquals("Basic (2)", remapped[0.0])
        assertEquals("Spin", remapped[1.0])
    }
}

class DefaultImportSecureZipTest {
    @Test
    fun import_after_validate_merges_metadata() =
        runTest {
            val meta =
                DomainJson.encodeAppData(
                    AppData(
                        danceStyles = listOf(DanceStyle(id = "stX", name = "X", moves = emptyList())),
                    ),
                )
            val inspector =
                FakeZipArchiveInspector(
                    defaultPaths = listOf("metadata.json", "videos/a.mp4"),
                )
            val validate = DefaultValidateSecureZip(inspector)
            val importer =
                DefaultImportSecureZip(
                    validate = validate,
                    metadataReader = MapSecureZipMetadataReader(mapOf("/tmp/a.zip" to meta)),
                )
            val into = AppData()
            val result = importer.import("/tmp/a.zip", into)
            assertTrue(result is com.midnightdancer.core.domain.usecase.ImportSecureZipResult.Success)
            val ok = result as com.midnightdancer.core.domain.usecase.ImportSecureZipResult.Success
            assertEquals(1, ok.data.danceStyles.size)
            assertEquals("stX", ok.data.danceStyles.single().id)
        }
}
