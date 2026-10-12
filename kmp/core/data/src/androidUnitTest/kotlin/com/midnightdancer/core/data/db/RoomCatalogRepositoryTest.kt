package com.midnightdancer.core.data.db

import androidx.test.core.app.ApplicationProvider
import com.midnightdancer.core.data.db.di.inMemoryCatalogDatabaseModule
import com.midnightdancer.core.data.migrate.CatalogMigrationHandOff
import com.midnightdancer.core.data.migrate.LegacyPrefsSource
import com.midnightdancer.core.data.migrate.catalog.CatalogExporter
import com.midnightdancer.core.data.migrate.catalog.CatalogMigrator
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.entity.Song
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.usecase.LoadLibrary
import com.midnightdancer.core.domain.usecase.SaveAppData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * C-13 Room tests on Android JVM (Robolectric) — Room Android builder needs Context.
 * Validates insert → Flow and golden AppData → Room → domain.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RoomCatalogRepositoryTest : KoinTest {
    private val goldenJson: String =
        """
        {
          "danceStyles": [
            {
              "id": "style-salsa",
              "name": "Salsa",
              "currentMoveId": "move-basic",
              "moves": [
                {
                  "id": "move-basic",
                  "name": "Basic",
                  "level": "beginner",
                  "description": "step",
                  "videoUri": "basic.mp4",
                  "masteryPercent": 40
                },
                {
                  "id": "move-turn",
                  "name": "Turn",
                  "level": "intermediate",
                  "description": null,
                  "videoUri": "turn.mp4",
                  "masteryPercent": 10
                }
              ]
            }
          ],
          "songs": [
            {
              "id": "song-1",
              "title": "Midnight Track",
              "danceStyle": "Salsa",
              "level": "beginner",
              "fileName": "midnight.mp3",
              "duration": 180.5,
              "sizeBytes": 2048,
              "playbackSpeed": 1.0
            }
          ],
          "choreographies": [
            {
              "id": "ch-1",
              "name": "Routine A",
              "songId": "song-1",
              "styleId": "style-salsa",
              "timeline": {
                "0.0": "style-salsa::move-basic",
                "1.5": "move-turn"
              },
              "startTime": 0.0,
              "endTime": 60.0
            }
          ],
          "settings": {
            "ui_language": "ru",
            "legalTermsAccepted": true
          }
        }
        """.trimIndent()

    @Before
    fun setUp() {
        bindCatalogDatabaseContext(ApplicationProvider.getApplicationContext())
        startKoin {
            modules(inMemoryCatalogDatabaseModule("c13-test.db"))
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun insertSong_observeFlow_emitsSong() =
        runTest {
            val load = get<LoadLibrary>()
            val save = get<SaveAppData>()
            val song =
                Song(
                    id = "s-flow",
                    title = "Flow Song",
                    danceStyle = "Salsa",
                    level = "beginner",
                    fileName = "flow.mp3",
                    duration = 10.0,
                    sizeBytes = 100,
                )

            save.upsertSong(song)

            val observed = load.observe().first { it.songs.any { s -> s.id == "s-flow" } }
            assertEquals("Flow Song", observed.songs.single { it.id == "s-flow" }.title)
        }

    @Test
    fun moveDao_afterUpsertStyle_hasNestedMoves() =
        runTest {
            val save = get<SaveAppData>()
            val db = get<CatalogDatabase>()
            save.upsertStyle(
                DanceStyle(
                    id = "st1",
                    name = "Bachata",
                    moves =
                        listOf(
                            Move(
                                id = "m1",
                                name = "Basic",
                                level = "beginner",
                                masteryPercent = 0,
                            ),
                        ),
                ),
            )
            val moves = db.moveDao().getAll()
            assertEquals(1, moves.size)
            assertEquals("st1", moves.single().styleId)
            assertEquals("Basic", moves.single().name)

            val flowMoves = db.moveDao().observeAll().first { it.isNotEmpty() }
            assertEquals(1, flowMoves.size)
        }

    @Test
    fun goldenAppData_toRoom_toDomain_roundTrip() =
        runTest {
            val migrator = get<CatalogMigrator>()
            val exporter = get<CatalogExporter>()
            val expected = DomainJson.decodeAppData(goldenJson)

            val result =
                migrator.migrateFromHandOff(
                    CatalogMigrationHandOff(
                        appDataJson = goldenJson,
                        source = LegacyPrefsSource.FilesystemMetadataJson,
                    ),
                )
            assertTrue(result.applied)
            assertFalse(result.skippedAlreadyMigrated)
            assertEquals(1, result.entityCounts.styles)
            assertEquals(2, result.entityCounts.moves)
            assertEquals(1, result.entityCounts.songs)
            assertEquals(1, result.entityCounts.choreographies)

            val loaded = exporter.exportAppData()
            assertEquals(expected.danceStyles, loaded.danceStyles)
            assertEquals(expected.songs, loaded.songs)
            assertEquals(expected.choreographies, loaded.choreographies)
            assertEquals("ru", loaded.settings["ui_language"]?.jsonPrimitive?.content)
            assertEquals(
                "true",
                loaded.settings["legalTermsAccepted"]?.jsonPrimitive?.content,
            )

            val again =
                migrator.migrateFromHandOff(
                    CatalogMigrationHandOff(
                        appDataJson = goldenJson,
                        source = LegacyPrefsSource.FilesystemMetadataJson,
                    ),
                )
            assertTrue(again.skippedAlreadyMigrated)
            assertFalse(again.applied)

            migrator.importAppDataJson(goldenJson, markDone = false)
            val after = exporter.exportAppData()
            assertEquals(1, after.danceStyles.size)
            assertEquals(2, after.danceStyles.single().moves.size)
            assertEquals(1, after.songs.size)
            assertEquals(1, after.choreographies.size)
            assertEquals(
                mapOf(0.0 to "style-salsa::move-basic", 1.5 to "move-turn"),
                after.choreographies.single().timeline,
            )
        }

    @Test
    fun koin_providesLoadLibraryAndSaveAppData() {
        val load = get<LoadLibrary>()
        val save = get<SaveAppData>()
        assertTrue(load is RoomCatalogRepository)
        assertTrue(save is RoomCatalogRepository)
    }
}
