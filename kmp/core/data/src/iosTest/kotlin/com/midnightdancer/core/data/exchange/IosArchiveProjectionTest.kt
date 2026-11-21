package com.midnightdancer.core.data.exchange

import com.midnightdancer.core.domain.entity.*
import com.midnightdancer.core.domain.serialization.DomainJson
import kotlinx.serialization.json.*
import kotlin.test.*

class IosArchiveProjectionTest {
    @Test fun exact_archive_preserves_all_materials_styles_and_legacy_editor_collision() {
        val editor = """{"version":1,"items":[{"id":"same","title":"Archive editor","style":"Archive style","snapshot":null}]}"""
        val data = AppData(
            danceStyles = listOf(DanceStyle("archive-style", "Archive style", listOf(Move("move", "Archive video", "1")))),
            songs = listOf(Song("song", "Archive music", "archive-style", "1", "song.wav", 1.0, 1)),
            choreographies = listOf(Choreography("same", "Archive legacy", "song", "archive-style", endTime = 1.0)),
            settings = JsonObject(mapOf("editorVaultV1" to JsonPrimitive(editor))))
        val snapshot = IosArchiveInspector.project(DomainJson.encodeAppData(data))
        assertEquals(listOf("Archive video"), snapshot.elements.map { it.title })
        assertEquals(listOf("Archive music"), snapshot.songs.map { it.title })
        assertEquals(listOf("Archive legacy", "Archive editor"), snapshot.choreographies.map { it.title })
        assertEquals(2, snapshot.choreographies.map { it.id }.distinct().size)
        assertEquals(listOf("Archive style"), snapshot.styles.map { it.name })
    }
    @Test fun unsupported_or_malformed_editor_cannot_fall_back_to_global_library() {
        for (raw in listOf("""{"version":2,"items":[]}""", """{"version":1,"items":[{"id":"only-id"}]}""")) {
            val data = AppData(settings = JsonObject(mapOf("editorVaultV1" to JsonPrimitive(raw))))
            assertFails { IosArchiveInspector.project(DomainJson.encodeAppData(data)) }
        }
    }
    @Test fun empty_archive_snapshot_does_not_invent_demo_or_default_materials() {
        val snapshot = IosArchiveInspector.project(DomainJson.encodeAppData(AppData()))
        assertTrue(snapshot.styles.isEmpty()); assertTrue(snapshot.elements.isEmpty())
        assertTrue(snapshot.songs.isEmpty()); assertTrue(snapshot.choreographies.isEmpty())
    }
}
