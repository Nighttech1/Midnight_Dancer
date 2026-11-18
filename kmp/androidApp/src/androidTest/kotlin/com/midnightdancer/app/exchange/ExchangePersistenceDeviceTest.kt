package com.midnightdancer.app.exchange

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midnightdancer.feature.choreography.logic.ChoreographySnapshot
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultItem
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultStore
import com.midnightdancer.feature.choreography.logic.EditorClip
import com.midnightdancer.feature.exchange.logic.SavedSet
import com.midnightdancer.feature.exchange.logic.SavedSetOrigin
import com.midnightdancer.feature.choreography.logic.VaultStatus
import com.midnightdancer.feature.choreography.logic.decodeVaultItems
import com.midnightdancer.feature.choreography.logic.encodeVaultItems
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real filesystem checks exclusively in an isolated ContextWrapper, never the user's filesDir. */
@RunWith(AndroidJUnit4::class)
class ExchangePersistenceDeviceTest {
    private lateinit var base: Context
    private lateinit var sandbox: File
    private lateinit var isolated: Context
    private var originalStore: List<ChoreographyVaultItem> = emptyList()

    @Before fun setUp() {
        base = InstrumentationRegistry.getInstrumentation().targetContext
        sandbox = File(base.cacheDir, "exchange-device-test-${UUID.randomUUID()}").apply { mkdirs() }
        isolated = object : ContextWrapper(base) {
            override fun getFilesDir() = File(sandbox, "files").apply { mkdirs() }
            override fun getCacheDir() = File(sandbox, "cache").apply { mkdirs() }
        }
        originalStore = ChoreographyVaultStore.all()
    }

    @After fun tearDown() {
        ChoreographyVaultStore.replaceAll(originalStore)
        check(sandbox.canonicalPath.startsWith(base.cacheDir.canonicalPath + File.separator))
        sandbox.deleteRecursively()
    }

    @Test fun cards_and_owned_archives_survive_adapter_recreation_and_source_overwrite() {
        val source = File(isolated.filesDir, "inbox.dance").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val persister = AndroidSavedSetsPersister(isolated)
        val owned = persister.retainArchive(source.path, "imported-1-1")
        val row = SavedSet("imported-1-1", "Мой сет", "s", origin = SavedSetOrigin.Imported, archivePath = owned)
        persister.write(listOf(row))
        source.writeBytes(byteArrayOf(9, 9))
        val reopened = AndroidSavedSetsPersister(isolated)
        assertEquals(listOf(row), reopened.read())
        assertArrayEquals(byteArrayOf(1, 2, 3), File(reopened.read()!!.single().archivePath!!).readBytes())
        reopened.write(emptyList())
        assertEquals(emptyList<SavedSet>(), AndroidSavedSetsPersister(isolated).read())
    }

    @Test fun older_card_metadata_without_optional_fields_loads_safely() {
        File(isolated.filesDir, "saved_sets_v1.json").writeText(
            """{"schemaVersion":1,"sets":[{"id":"old","name":"Old","styleId":"s","origin":"Imported"}]}""",
        )
        val restored = AndroidSavedSetsPersister(isolated).read()!!.single()
        assertEquals("old", restored.id)
        assertNull(restored.archivePath)
        assertTrue(restored.choreoIds.isEmpty())
    }

    @Test fun editor_transfer_preserves_trim_speed_and_volume_merges_and_can_rollback() {
        val media = File(isolated.filesDir, "dance.mp4").apply { writeBytes(byteArrayOf(3, 2, 1)) }
        val before = listOf(ChoreographyVaultItem(
            id = "dance-1", title = "Dance", status = VaultStatus.Ready, style = "Sensual",
            bpm = 120, durationLabel = "00:08", trackName = "Track", chrId = "dance-1",
            snapshot = ChoreographySnapshot(
                title = "Dance", style = "Sensual",
                elementClips = listOf(EditorClip("Turn", 8000, media.path, speed = 0.75, sourceStartMs = 1200)),
                musicVolume = 0.4f,
            ),
        ))
        val vaultFile = File(isolated.filesDir, "choreography_vault_v1.json")
        vaultFile.writeText(encodeVaultItems(before))
        val bridge = AndroidChoreographyExchangeBridge(isolated)
        assertEquals("dance-1", bridge.catalog().single().id)
        val transfer = bridge.export(setOf("dance-1"))
        assertEquals(1, transfer.media.size)
        val portableClip = decodeVaultItems(transfer.payloadJson).single().snapshot!!.elementClips.single()
        assertTrue(portableClip.mediaUri!!.startsWith("videos/editor-"))
        val destination = File(isolated.filesDir, "imported.mp4").apply { writeBytes(media.readBytes()) }
        val changed = decodeVaultItems(transfer.payloadJson).map { it.copy(title = "Variant", snapshot = it.snapshot?.copy(title = "Variant")) }
        val transaction = bridge.prepareImport(encodeVaultItems(changed),
            mapOf(transfer.media.single().archivePath to destination.path), mapOf("Sensual" to "Bachata"))
        assertEquals(before, decodeVaultItems(vaultFile.readText())) // prepare does not mutate user state
        transaction.commit()
        val after = decodeVaultItems(vaultFile.readText())
        assertEquals(2, after.size)
        assertEquals(before.first(), after.first())
        assertNotEquals("dance-1", after.last().id)
        val imported = after.last().snapshot!!
        assertEquals("Bachata", imported.style)
        assertEquals(1200L, imported.elementClips.single().sourceStartMs)
        assertEquals(0.75, imported.elementClips.single().speed, 0.0)
        assertEquals(0.4f, imported.musicVolume, 0f)
        assertEquals(destination.path, imported.elementClips.single().mediaUri)
        transaction.rollback()
        assertEquals(before, decodeVaultItems(vaultFile.readText()))
        // Restoring an exact exported snapshot is idempotent, including a Free user at quota.
        val freeBridge = AndroidChoreographyExchangeBridge(isolated,
            com.midnightdancer.core.domain.settings.CreationAccessPolicy { count -> count < 1 })
        val restore = freeBridge.prepareImport(transfer.payloadJson,
            mapOf(transfer.media.single().archivePath to destination.path))
        restore.commit()
        assertEquals(before, decodeVaultItems(vaultFile.readText()))
        assertTrue(runCatching {
            freeBridge.prepareImport(encodeVaultItems(changed), mapOf(transfer.media.single().archivePath to destination.path))
        }.isFailure)
        assertEquals(before, decodeVaultItems(vaultFile.readText()))
    }

    @Test fun missing_or_absolute_imported_editor_media_is_rejected_before_commit() {
        val bridge = AndroidChoreographyExchangeBridge(isolated)
        val initial = listOf(ChoreographyVaultItem(
            "keep", "Keep", VaultStatus.Draft, "Sensual", bpm = 120, durationLabel = "00:00", trackName = "—", chrId = "keep",
            snapshot = ChoreographySnapshot("Keep", "Sensual", elementClips = listOf(EditorClip("X", 1000, "/private/foreign.mp4"))),
        ))
        val raw = encodeVaultItems(initial)
        File(isolated.filesDir, "choreography_vault_v1.json").writeText(raw)
        assertTrue(runCatching { bridge.prepareImport(raw, emptyMap()) }.isFailure)
        assertEquals(initial, decodeVaultItems(File(isolated.filesDir, "choreography_vault_v1.json").readText()))
    }

    @Test fun legacy_import_preserves_explicit_song_offset_event_boundaries_and_restores_idempotently() {
        val vaultFile = File(isolated.filesDir, "choreography_vault_v1.json").apply { writeText(encodeVaultItems(emptyList())) }
        val video1 = File(isolated.filesDir, "v1.mp4").apply { writeBytes(byteArrayOf(1)) }
        val video2 = File(isolated.filesDir, "v2.mp4").apply { writeBytes(byteArrayOf(2)) }
        val music = File(isolated.filesDir, "song.mp3").apply { writeBytes(byteArrayOf(3)) }
        val style = com.midnightdancer.core.domain.entity.DanceStyle("legacy-style", "Style", listOf(
            com.midnightdancer.core.domain.entity.Move("v1", "Step one", "1", videoUri = "v1"),
            com.midnightdancer.core.domain.entity.Move("v2", "Step two", "1", videoUri = "v2"),
        ))
        val legacy = com.midnightdancer.core.domain.entity.AppData(
            danceStyles = listOf(style),
            songs = listOf(com.midnightdancer.core.domain.entity.Song("song", "Song", "legacy-style", "1", "song.mp3", 30.0, 1)),
            choreographies = listOf(com.midnightdancer.core.domain.entity.Choreography(
                "legacy-dance", "Legacy dance", "song", "legacy-style",
                timeline = mapOf(12.0 to "legacy-style::v1", 16.0 to "legacy-style::v2"), startTime = 10.0, endTime = 20.0,
            )),
        )
        val paths = mapOf("videos/v1.mp4" to video1.path, "videos/v2.mp4" to video2.path, "music/song.mp3" to music.path)
        val bridge = AndroidChoreographyExchangeBridge(isolated)
        bridge.prepareImport("", paths, legacyData = legacy).commit()
        val item = decodeVaultItems(vaultFile.readText()).single()
        assertEquals("legacy-dance", item.id)
        assertEquals(VaultStatus.Draft, item.status)
        assertTrue(item.trackName.contains("старый формат"))
        val snapshot = item.snapshot!!
        assertEquals(listOf(2000L, 4000L, 4000L), snapshot.elementClips.map { it.durationMs })
        assertEquals(listOf("", "Step one", "Step two"), snapshot.elementClips.map { it.label })
        assertEquals(10000L, snapshot.musicClips.single().sourceStartMs)
        assertEquals(10000L, snapshot.musicClips.single().durationMs)
        bridge.prepareImport("", paths, legacyData = legacy).commit()
        assertEquals(1, decodeVaultItems(vaultFile.readText()).size)
        val roundtrip = bridge.export(setOf(item.id))
        val portable = decodeVaultItems(roundtrip.payloadJson).single().snapshot!!
        assertEquals(10000L, portable.musicClips.single().sourceStartMs)
        assertEquals(snapshot.elementClips.map { it.durationMs }, portable.elementClips.map { it.durationMs })
        assertEquals(3, roundtrip.media.size)
    }
}
