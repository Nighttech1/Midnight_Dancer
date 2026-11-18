package com.midnightdancer.core.data.styles

import androidx.test.core.app.ApplicationProvider
import com.midnightdancer.core.data.db.*
import com.midnightdancer.core.domain.entity.*
import com.midnightdancer.core.domain.styles.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CanonicalStyleRepositoryTest {
    private lateinit var db: CatalogDatabase
    private lateinit var repository: RoomCatalogRepository
    @Before fun setup() {
        bindCatalogDatabaseContext(ApplicationProvider.getApplicationContext())
        db = getCatalogDatabase(inMemoryCatalogDatabaseBuilder("styles-test.db"))
        repository = RoomCatalogRepository(db)
    }
    @After fun close() { db.close() }

    @Test fun bootstrapKeepsMoreThan24StylesAndConsumesLegacyNamesOnlyOnce() = runTest {
        repository.save(AppData(danceStyles = (1..30).map { DanceStyle("s$it", "Style $it") }))
        repository.bootstrap(listOf("Pref only"))
        assertEquals(32, repository.state.value!!.entries.size)
        val pref = repository.state.value!!.entries.single { it.name == "Pref only" }
        repository.remove(pref.id)
        val reopened = RoomCatalogRepository(db)
        reopened.bootstrap(listOf("Pref only", "Late stale"))
        assertFalse(reopened.state.value!!.entries.any { it.name == "Pref only" || it.name == "Late stale" })
        assertEquals(ReservedDanceStyle.ID, reopened.state.value!!.canonicalId(pref.id))
    }

    @Test fun deleteKeepsAllRowsAndLateWritersCannotResurrectStyle() = runTest {
        val move = Move("m", "A", "", videoUri = "owned.mp4")
        repository.save(AppData(danceStyles = listOf(DanceStyle("a", "Alpha", listOf(move), "m")),
            songs = listOf(Song("s", "Song", "Alpha", "", "owned.wav", 4.0, 20)),
            choreographies = listOf(Choreography("c", "Choreo", "s", "a", mapOf(0.0 to "a::m"), endTime = 4.0))))
        repository.bootstrap()
        repository.remove("a")
        repository.ensureDanceStyle("a", "Alpha")
        repository.upsertMove("a", move.copy(name = "Updated"))
        repository.upsertSong(Song("s", "Song", "Alpha", "", "owned.wav", 4.0, 20))
        val after = repository.load()
        assertFalse(after.danceStyles.any { it.id == "a" })
        assertEquals("Updated", after.danceStyles.single().moves.single().name)
        assertEquals("owned.mp4", after.danceStyles.single().moves.single().videoUri)
        assertEquals(ReservedDanceStyle.ID, after.songs.single().danceStyle)
        assertEquals("${ReservedDanceStyle.ID}::m", after.choreographies.single().timeline.values.single())
        assertEquals(1, db.moveDao().getAll().size)
        assertFails { repository.remove(ReservedDanceStyle.ID) }
        assertFails { repository.rename(ReservedDanceStyle.ID, "Renamed") }
    }

    @Test fun sameNameReaddCreatesFreshIdButOldAliasesStayDeleted() = runTest {
        repository.save(AppData(danceStyles = listOf(DanceStyle("old", "Alpha"))))
        repository.bootstrap(); repository.remove("old"); repository.add("Alpha")
        val snapshot = repository.state.value!!
        val fresh = snapshot.choiceId("Alpha")
        assertNotEquals("old", fresh)
        assertNotEquals(ReservedDanceStyle.ID, fresh)
        assertEquals(ReservedDanceStyle.ID, snapshot.canonicalId("Alpha"))
        repository.upsertSong(Song("new", "New", fresh, "", "owned.wav", 1.0, 1))
        assertEquals(fresh, repository.load().songs.single().danceStyle)
    }

    @Test fun malformedMetadataRollsBackWholeDelete() = runTest {
        repository.save(AppData(danceStyles = listOf(DanceStyle("a", "Alpha", listOf(Move("m", "A", ""))))))
        repository.bootstrap()
        val broken = repository.load().copy(settings = JsonObject(mapOf("editorVaultV1" to JsonPrimitive("broken"))))
        // Simulate an independently persisted invalid blob without going through normalizing SaveAppData.
        db.catalogMetaDao().upsert(com.midnightdancer.core.data.db.entity.CatalogMetaEntity("settings_json", broken.settings.toString()))
        val before = repository.load()
        assertFails { repository.remove("a") }
        assertEquals(before, repository.load())
        assertTrue(repository.state.value!!.entries.any { it.id == "a" })
    }

    @Test fun globalMoveOwnerConflictRejectsWholeSaveAndPreservesPreviousDatabase() = runTest {
        repository.save(AppData(danceStyles = listOf(DanceStyle("a", "Alpha", listOf(Move("original", "Original", ""))))))
        repository.bootstrap(listOf("Beta"))
        val before = repository.load()
        val beforeCatalog = repository.state.value
        val move = Move("duplicate", "First", "", videoUri = "owned.mp4")
        listOf("b", "__md_choreo_scoped__").forEach { secondOwner ->
            listOf(move, move.copy(videoUri = "different.mp4")).forEach { secondMove ->
                val conflict = before.copy(danceStyles = listOf(
                    DanceStyle("a", "Alpha", listOf(move)),
                    DanceStyle(secondOwner, "Second", listOf(secondMove)),
                ))
                assertFails { repository.save(conflict) }
                assertEquals(before, repository.load())
                assertEquals(beforeCatalog, repository.state.value)
            }
        }
    }

    @Test fun unsupportedRoomEditorSchemaOrScalarSnapshotRollsBackDeleteAndBootstrap() = runTest {
        repository.save(AppData(danceStyles = listOf(DanceStyle("a", "Alpha", listOf(Move("m", "Own", ""))))))
        repository.bootstrap()
        for (raw in listOf("""{"version":99,"items":[{"style":"a"}]}""",
            """{"version":1,"items":[{"style":"a","snapshot":42}]}""")) {
            db.catalogMetaDao().upsert(com.midnightdancer.core.data.db.entity.CatalogMetaEntity("settings_json",
                JsonObject(mapOf("editorVaultV1" to JsonPrimitive(raw))).toString()))
            val before = repository.load()
            val catalog = repository.state.value
            assertFails { repository.remove("a") }
            assertEquals(before, repository.load())
            assertEquals(catalog, repository.state.value)
            // A new installation/bootstrap must reject the same unsupported metadata before marker commit.
            db.catalogMetaDao().delete("canonical_styles_v1")
            val reopened = RoomCatalogRepository(db)
            assertFails { reopened.bootstrap() }
            assertEquals(before, reopened.load())
            assertNull(reopened.state.value)
            // Restore a valid marker for the next delete attempt without touching the fixture payload.
            db.catalogMetaDao().upsert(com.midnightdancer.core.data.db.entity.CatalogMetaEntity("canonical_styles_v1",
                com.midnightdancer.core.domain.serialization.DomainJson.format.encodeToString(DanceStyleCatalogSnapshot.serializer(), catalog!!)))
        }
    }
}
