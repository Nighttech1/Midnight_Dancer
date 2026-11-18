package com.midnightdancer.app.styles

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.midnightdancer.app.choreography.AndroidChoreographyVaultPersister
import com.midnightdancer.app.exchange.AndroidSavedSetsPersister
import com.midnightdancer.core.domain.styles.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import androidx.room.Room
import com.midnightdancer.app.bootstrapAndroidStyleCatalog
import com.midnightdancer.core.data.db.CatalogDatabase
import com.midnightdancer.core.data.db.RoomCatalogRepository
import com.midnightdancer.core.data.db.getCatalogDatabase
import com.midnightdancer.core.domain.entity.*
import com.midnightdancer.core.domain.settings.DanceStyleConfig
import com.midnightdancer.core.domain.usecase.ReadWriteUserPrefs
import com.midnightdancer.feature.choreography.logic.*
import com.midnightdancer.feature.exchange.logic.*
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** Unique private fixture; no product catalog/files or archived payloads are modified. */
@RunWith(AndroidJUnit4::class)
class CanonicalStylePersistenceDeviceTest {
    private lateinit var base: Context
    private lateinit var sandbox: File
    private lateinit var context: Context
    private val snapshot = DanceStyleCatalogSnapshot(2,
        listOf(DanceStyleEntry(ReservedDanceStyle.ID, ReservedDanceStyle.NAME, true)), mapOf("a" to ReservedDanceStyle.ID))
    private val manager = object : ManageDanceStyles {
        override val state = MutableStateFlow<DanceStyleCatalogSnapshot?>(snapshot).asStateFlow()
        override suspend fun bootstrap(legacyNames: List<String>) = Unit
        override suspend fun add(name: String) = Unit
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun remove(id: String) = Unit
    }
    @Before fun setup() {
        base = InstrumentationRegistry.getInstrumentation().targetContext
        sandbox = File(base.cacheDir, "style-projection-qa-${UUID.randomUUID()}").apply { check(mkdirs()) }
        context = object : ContextWrapper(base) { override fun getFilesDir() = sandbox }
    }
    @After fun cleanup() {
        check(sandbox.canonicalFile.parentFile == base.cacheDir.canonicalFile && sandbox.name.startsWith("style-projection-qa-"))
        sandbox.deleteRecursively()
    }

    @Test fun vaultAtomicProjectionPreservesUnknownsAndRejectsMalformedOrUnsupportedWithoutReplacing() {
        val raw = """{"version":1,"unknownRoot":true,"items":[{"id":"c","title":"Own","status":"Ready","style":"a","unknownItem":[3],"snapshot":{"title":"Own","style":"a","unknownSnapshot":9,"musicClips":[{"label":"Music","durationMs":1000,"mediaUri":"owned.wav","unknownClip":true}]}}]}"""
        val file = File(sandbox, "choreography_vault_v1.json").apply { writeText(raw) }
        val disk = AndroidChoreographyVaultPersister(context, manager)
        disk.refreshStyles()
        val projected = file.readBytes()
        val expected = Json.parseToJsonElement(raw.replace("\"style\":\"a\"", "\"style\":\"${ReservedDanceStyle.ID}\""))
        assertEquals(expected, Json.parseToJsonElement(file.readText()))
        for (invalid in listOf("broken", """{"version":99,"items":[]}""")) {
            assertTrue(runCatching { disk.writeRaw(invalid) }.isFailure)
            assertArrayEquals(projected, file.readBytes())
            file.writeText(invalid)
            val prior = file.readBytes()
            assertTrue(runCatching { disk.refreshStyles() }.isFailure)
            assertArrayEquals(prior, file.readBytes())
            file.writeBytes(projected)
        }
    }

    @Test fun setProjectionPreservesImportedRawFieldsArchiveBytesAndRefusesInvalidSchema() {
        val archive = File(sandbox, "saved_set_archives/history.dance").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(79) { it.toByte() }) }
        val payload = archive.readBytes()
        val raw = """{"schemaVersion":1,"futureRoot":true,"sets":[{"id":"mine","name":"Own","styleId":"a","styleName":"Alpha","futureMine":9},{"id":"import","name":"Archive","origin":"Imported","styleId":"a","styleName":"Historical","archivePath":"${archive.absolutePath.replace("\\", "\\\\")}","unknownImported":[1,2]}]}"""
        val file = File(sandbox, "saved_sets_v1.json").apply { writeText(raw) }
        val disk = AndroidSavedSetsPersister(context, manager)
        disk.refreshStyles()
        val after = Json.parseToJsonElement(file.readText()).jsonObject
        val before = Json.parseToJsonElement(raw).jsonObject
        assertEquals(before["futureRoot"], after["futureRoot"])
        assertEquals(before.getValue("sets").jsonArray[1], after.getValue("sets").jsonArray[1])
        assertEquals(ReservedDanceStyle.ID, after.getValue("sets").jsonArray[0].jsonObject.getValue("styleId").jsonPrimitive.content)
        assertArrayEquals(payload, archive.readBytes())
        for (invalid in listOf("broken", """{"schemaVersion":99,"sets":[]}""")) {
            file.writeText(invalid)
            val prior = file.readBytes()
            assertTrue(runCatching { disk.refreshStyles() }.isFailure)
            assertArrayEquals(prior, file.readBytes())
            assertArrayEquals(payload, archive.readBytes())
        }
    }

    @Test fun productionBootstrapWithoutOptionalPrefsAcceptsValidLegacyFilesAndPreservesAllMaterialIdentities() = runBlocking {
        val previousStyles = org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<ManageDanceStyles>()?.state?.value
        val database = getCatalogDatabase(Room.inMemoryDatabaseBuilder(base, CatalogDatabase::class.java))
        val repository = RoomCatalogRepository(database)
        val graph = koinApplication { modules(module { single<ManageDanceStyles> { repository } }) }
        try {
            val rows = (0..2).map { style -> DanceStyle("legacy-$style", "Legacy $style",
                (0 until 14).filter { it % 3 == style }.map { Move("m$it", "Fixture $it", "", videoUri = "owned-$it.mp4") }) }
            val songs = (0..3).map { Song("song$it", "Fixture song $it", "Legacy 0", "", "owned-$it.wav", 1.0, 10) }
            repository.save(AppData(danceStyles = rows, songs = songs))
            val vault = (0..4).map { index -> ChoreographyVaultItem("fixture-c$index", "Fixture $index", VaultStatus.Ready,
                "Legacy 0", bpm = 120, durationLabel = "00:01", trackName = "Fixture music", chrId = "fixture-c$index",
                snapshot = if (index != 0) null else ChoreographySnapshot("Fixture 0", "Legacy 0",
                    musicClips = listOf(EditorClip("Own music", 1000, "owned-0.wav")))) }
            AndroidChoreographyVaultPersister(context).writeRaw(encodeVaultItems(vault))
            val archives = (4..5).map { index -> File(sandbox, "saved_set_archives/fixture-$index.dance").apply {
                parentFile!!.mkdirs(); writeBytes(ByteArray(80 + index) { it.toByte() })
            } }
            val archiveBytes = archives.map { it.readBytes() }
            val sets = (0..6).map { index -> SavedSet("fixture-set$index", "Fixture set $index",
                if (index == 6) "" else "legacy-0", if (index == 6) "" else "Legacy 0",
                origin = if (index in 4..5) SavedSetOrigin.Imported else SavedSetOrigin.Mine,
                archivePath = if (index in 4..5) archives[index - 4].path else null) }
            AndroidSavedSetsPersister(context).write(sets)
            assertNull(graph.koin.getOrNull<ReadWriteUserPrefs>())
            val stages = mutableListOf<String>()
            // Same function called by Application, a real Room manager, real AtomicFiles, no prefs binding.
            bootstrapAndroidStyleCatalog(context, graph.koin.get(), graph.koin.getOrNull(), stages::add)
            assertEquals("ready", stages.last())
            val catalog = repository.state.value!!
            assertTrue(catalog.entries.map { it.name }.containsAll(DanceStyleConfig.DEFAULT_NAMES))
            assertTrue(catalog.entries.any { it.id == ReservedDanceStyle.ID })
            val after = repository.load()
            assertEquals(rows.flatMap { it.moves }.toSet(), after.danceStyles.flatMap { it.moves }.toSet())
            assertEquals(songs.map { it.id to it.fileName }, after.songs.map { it.id to it.fileName })
            val restoredVault = decodeVaultItems(AndroidChoreographyVaultPersister(context).readRaw()!!)
            assertEquals(vault.map { it.id }, restoredVault.map { it.id })
            assertEquals(4, restoredVault.count { it.snapshot == null })
            assertEquals(vault[0].snapshot!!.musicClips, restoredVault[0].snapshot!!.musicClips)
            val restoredSets = AndroidSavedSetsPersister(context).read()!!
            assertEquals(7, restoredSets.size)
            assertEquals(sets.filter { it.origin == SavedSetOrigin.Imported }, restoredSets.filter { it.origin == SavedSetOrigin.Imported })
            archives.forEachIndexed { index, file -> assertArrayEquals(archiveBytes[index], file.readBytes()) }
            val deletedDefault = catalog.entries.single { it.name == "Salsa" }
            repository.remove(deletedDefault.id)
            bootstrapAndroidStyleCatalog(context, repository, null)
            assertFalse(repository.state.value!!.entries.any { it.id == deletedDefault.id || it.name == "Salsa" })
            assertEquals(14, repository.load().danceStyles.sumOf { it.moves.size })
        } finally {
            ChoreoCatalog.replaceStyleCatalog(previousStyles)
            graph.close(); database.close()
        }
    }
}
