package com.midnightdancer.shared

import com.midnightdancer.core.domain.entity.*
import com.midnightdancer.feature.choreography.logic.*
import kotlinx.serialization.json.*
import kotlin.test.*

class IosChoreographyImportBridgeTest {
    @Test fun exactRepeatedClipsAppendWithoutReplacingCollisionAndKeepUnknownExistingFields() {
        val existing = item("same", "Original")
        val vault = Vault(withUnknown(encodeVaultItems(listOf(existing))))
        val original = Json.parseToJsonElement(vault.raw!!).jsonObject
        val clip = EditorClip("Repeat", 1250, "videos/a.mp4", 1.5, 2300)
        val incoming = item("same", "Incoming", ChoreographySnapshot("Incoming", "old", listOf(clip, clip),
            listOf(EditorClip("Song", 4000, "music/a.wav", sourceStartMs = 850)), musicVolume = 0.3f))
        val prepared = bridge(vault).prepareImport(encodeVaultItems(listOf(incoming)), media(), mapOf("old" to "new-style"), AppData())
        // Another legitimate entry arrives after prepare; commit must append to that latest document.
        val newer = item("concurrent", "Concurrent")
        vault.raw = append(vault.raw!!, newer)
        prepared.commit(); prepared.commit()
        val current = decodeVaultItems(vault.raw!!)
        assertEquals(3, current.size)
        assertEquals(existing, current.single { it.id == "same" })
        assertEquals(newer, current.single { it.id == "concurrent" })
        val imported = current.single { it.title == "Incoming" }
        assertNotEquals("same", imported.id)
        assertEquals(imported.id, imported.chrId)
        assertEquals("new-style", imported.style)
        val snapshot = assertNotNull(imported.snapshot)
        assertEquals("new-style", snapshot.style)
        assertEquals(listOf(clip.copy(mediaUri = "/private/a.mp4"), clip.copy(mediaUri = "/private/a.mp4")), snapshot.elementClips)
        assertEquals(850L, snapshot.musicClips.single().sourceStartMs)
        assertEquals(0.3f, snapshot.musicVolume)
        val after = Json.parseToJsonElement(vault.raw!!).jsonObject
        assertEquals(original["futureHeader"], after["futureHeader"])
        assertEquals(original.getValue("items").jsonArray.first(), after.getValue("items").jsonArray.first())
    }

    @Test fun portableMappedReferenceIsValidatedAtCurrentPathButPersistedWithoutContainerPath() {
        val vault = Vault(null)
        val portable = "md-import-v1/00000000-0000-0000-0000-000000000001/a.mp4"
        val clip = EditorClip("Trim", 1200, "videos/a.mp4", 1.25, 800)
        val item = item("new", "New", ChoreographySnapshot("New", "old", listOf(clip, clip)))
        bridge(vault).prepareImport(encodeVaultItems(listOf(item)), mapOf("videos/a.mp4" to portable), emptyMap(), AppData()).commit()
        val clips = decodeVaultItems(vault.raw!!).single().snapshot!!.elementClips
        assertEquals(listOf(clip.copy(mediaUri = portable), clip.copy(mediaUri = portable)), clips)
    }

    @Test fun invalidSourceTimingAndFutureSchemaFailBeforeWrites() {
        val vault = Vault(encodeVaultItems(listOf(item("old", "Old"))))
        val before = vault.raw
        fun check(clip: EditorClip, mapping: Map<String, String> = media()) {
            val incoming = item("new", "New", ChoreographySnapshot("New", "old", listOf(clip)))
            assertFails { bridge(vault).prepareImport(encodeVaultItems(listOf(incoming)), mapping, emptyMap(), AppData()) }
        }
        check(EditorClip("Missing", 1000, "videos/missing.mp4"))
        check(EditorClip("Escape", 1000, "../a.mp4"))
        check(EditorClip("Wrong lane", 1000, "music/a.wav"))
        check(EditorClip("Negative", 1000, "videos/a.mp4", sourceStartMs = -1))
        check(EditorClip("Overflow", 1000, "videos/a.mp4", sourceStartMs = Long.MAX_VALUE))
        check(EditorClip("Invalid mapped path", 1000, "videos/a.mp4"), mapOf("videos/a.mp4" to "https://foreign/a.mp4"))
        assertFails { bridge(vault).prepareImport("{\"version\":99,\"items\":[]}", emptyMap(), emptyMap(), AppData()) }
        assertEquals(before, vault.raw); assertEquals(0, vault.writes)
    }

    @Test fun detectedConcurrentWriteAbortsBeforeReplacingLatestRows() {
        val vault = Vault(encodeVaultItems(listOf(item("old", "Old"))))
        val prepared = bridge(vault).prepareImport(encodeVaultItems(listOf(item("new", "New"))), emptyMap(), emptyMap(), AppData())
        vault.beforeRead = { count -> if (count == 3) vault.raw = append(vault.raw!!, item("other", "Other")) }
        assertFails { prepared.commit() }
        prepared.rollback()
        assertEquals(listOf("old", "other"), decodeVaultItems(vault.raw!!).map { it.id })
        assertEquals(0, vault.writes)
    }

    @Test fun rollbackRemovesOnlyOwnUnchangedRowsAndPreservesEditedImportedAndForeignRows() {
        val vault = Vault(withUnknown(encodeVaultItems(listOf(item("old", "Old")))))
        val prepared = bridge(vault).prepareImport(encodeVaultItems(listOf(item("a", "A"), item("b", "B"))), emptyMap(), emptyMap(), AppData())
        prepared.commit()
        val root = Json.parseToJsonElement(vault.raw!!).jsonObject
        val edited = root.getValue("items").jsonArray.map { element ->
            if (element.jsonObject["id"]?.jsonPrimitive?.content != "a") element
            else JsonObject(element.jsonObject.toMutableMap().apply { put("title", JsonPrimitive("User edited")) })
        }
        vault.raw = append(JsonObject(root.toMutableMap().apply { put("items", JsonArray(edited)) }).toString(), item("foreign", "Foreign"))
        assertFails { prepared.rollback() }
        val current = decodeVaultItems(vault.raw!!)
        assertEquals(listOf("old", "a", "foreign"), current.map { it.id })
        assertEquals("User edited", current.single { it.id == "a" }.title)
        assertNotNull(Json.parseToJsonElement(vault.raw!!).jsonObject["futureHeader"])
        prepared.rollback() // Already removed safe rows; never retry removal of the changed row.
    }

    @Test fun atomicWriterThatCommitsThenThrowsCanRollbackOnlyItsNewRows() {
        val vault = Vault(encodeVaultItems(listOf(item("old", "Old"))))
        val before = Json.parseToJsonElement(vault.raw!!)
        val prepared = bridge(vault).prepareImport(encodeVaultItems(listOf(item("new", "New"))), emptyMap(), emptyMap(), AppData())
        vault.failAfterWrite = true
        assertFails { prepared.commit() }
        vault.failAfterWrite = false
        assertFails { prepared.commit() } // Ambiguous failed writes cannot be retried into duplicate append.
        prepared.rollback()
        assertEquals(before, Json.parseToJsonElement(vault.raw!!))
    }

    @Test fun legacyProjectionUsesExactSongScopedMoveAndSongAbsoluteTrim() {
        val vault = Vault(null)
        val legacy = Choreography("legacy", "Legacy", "song", "style", mapOf(1.0 to "style::move", 3.0 to "style::move"), 1.5, 5.5, 2.0)
        val data = AppData(danceStyles = listOf(DanceStyle("style", "Style", moves = listOf(Move("move", "Move", "", videoUri = "/old-phone/video.mp4")))),
            songs = listOf(Song("song", "Song", "style", "", "/old-phone/song.wav", 20.0, 100)), choreographies = listOf(legacy))
        val prefix = "md-import-v1/00000000-0000-0000-0000-000000000001/"
        val mapping = mapOf("videos/move.mp4" to prefix + "move.mp4", "music/song.wav" to prefix + "song.wav")
        bridge(vault).prepareImport("", mapping, mapOf("style" to "local-style"), data).commit()
        val imported = decodeVaultItems(vault.raw!!).single()
        assertEquals("local-style", imported.style)
        val snapshot = assertNotNull(imported.snapshot)
        assertEquals(listOf(1500L, 2500L), snapshot.elementClips.map { it.durationMs })
        assertEquals(listOf(500L, 0L), snapshot.elementClips.map { it.sourceStartMs })
        assertEquals(listOf(2.0, 2.0), snapshot.elementClips.map { it.speed })
        assertEquals(1500L, snapshot.musicClips.single().sourceStartMs)
        assertEquals(4000L, snapshot.musicClips.single().durationMs)
        assertEquals(prefix + "song.wav", snapshot.musicClips.single().mediaUri)
        assertEquals(listOf(prefix + "move.mp4", prefix + "move.mp4"), snapshot.elementClips.map { it.mediaUri })
    }

    private fun bridge(vault: Vault) = IosChoreographyExportBridge(vault, Driver())
    private fun media() = mapOf("videos/a.mp4" to "/private/a.mp4", "music/a.wav" to "/private/a.wav")
    private fun item(id: String, title: String, snapshot: ChoreographySnapshot? = null) = ChoreographyVaultItem(id, title,
        VaultStatus.Ready, "old", bpm = 120, durationLabel = "00:04", trackName = "", chrId = id, snapshot = snapshot)
    private fun append(raw: String, item: ChoreographyVaultItem): String {
        val root = Json.parseToJsonElement(raw).jsonObject
        val new = Json.parseToJsonElement(encodeVaultItems(listOf(item))).jsonObject.getValue("items").jsonArray.single()
        return JsonObject(root.toMutableMap().apply { put("items", JsonArray(root.getValue("items").jsonArray + new)) }).toString()
    }
    private fun withUnknown(raw: String): String {
        val root = Json.parseToJsonElement(raw).jsonObject
        val rows = root.getValue("items").jsonArray.map { JsonObject(it.jsonObject.toMutableMap().apply { put("futureRow", JsonPrimitive(42)) }) }
        return JsonObject(root.toMutableMap().apply { put("futureHeader", JsonPrimitive("preserve")); put("items", JsonArray(rows)) }).toString()
    }
    private class Vault(var raw: String?) : ChoreographyVaultPersister {
        var writes = 0; var reads = 0; var failAfterWrite = false
        var beforeRead: ((Int) -> Unit)? = null
        override fun readRaw(): String? { reads++; beforeRead?.invoke(reads); return raw }
        override fun writeRaw(json: String) { writes++; raw = json; if (failAfterWrite) error("fixture-after-atomic-write") }
    }
    private class Driver : IosArchiveDriver {
        override fun resolveMedia(reference: String): String? = when {
            reference.startsWith("/private/") -> reference
            reference.startsWith("md-import-v1/00000000-0000-0000-0000-000000000001/") -> "/private/imports/" + reference.substringAfterLast('/')
            else -> null
        }
        override fun mediaSize(path: String) = 100L
        override fun mediaSha256(path: String) = "a".repeat(64)
        override fun decryptToPrivateZip(containerPath: String, destinationPath: String, bundleId: String, shouldCancel: () -> Boolean) = error("unused")
        override fun encryptPrivateZip(zipPath: String, destinationPath: String, bundleId: String, shouldCancel: () -> Boolean) = error("unused")
    }
}
