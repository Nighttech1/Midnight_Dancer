package com.midnightdancer.core.domain.styles

import com.midnightdancer.core.domain.entity.*
import kotlinx.serialization.json.*
import kotlin.test.*

class CanonicalStylesTest {
    private val catalog = DanceStyleCatalogSnapshot(2,
        listOf(DanceStyleEntry(ReservedDanceStyle.ID, ReservedDanceStyle.NAME, true), DanceStyleEntry("fresh", "Old")),
        mapOf("deleted" to ReservedDanceStyle.ID), mapOf("old" to ReservedDanceStyle.ID))

    @Test fun freshExplicitChoiceAndHistoricalNameHaveDifferentIdentity() {
        assertEquals("fresh", catalog.choiceId("Old"))
        assertEquals(ReservedDanceStyle.ID, catalog.canonicalId("Old"))
        assertEquals("fresh", catalog.canonicalId("fresh"))
        assertEquals(ReservedDanceStyle.ID, catalog.canonicalId("deleted"))
        assertEquals(ReservedDanceStyle.ID, catalog.canonicalId(""))
    }

    @Test fun reassignmentPreservesMaterialPayloadAndEncodedTimeline() {
        val move = Move("m", "Own move", "tag", videoUri = "file:///owned/video.mp4", masteryPercent = 72)
        val song = Song("s", "Own song", "Old", "tag", "file:///owned/song.wav", 3.25, 99)
        val choreo = Choreography("c", "Own choreography", "s", "deleted", mapOf(1.5 to "deleted::m", 2.0 to "legacy"), endTime = 9.0)
        val data = AppData(listOf(DanceStyle("deleted", "Old", listOf(move), "m")), listOf(song), listOf(choreo))
        val result = data.normalizeLocalStyles(catalog)
        val reserved = result.danceStyles.first { it.id == ReservedDanceStyle.ID }
        assertEquals(listOf(move), reserved.moves)
        assertEquals("m", reserved.currentMoveId)
        assertEquals(song.copy(danceStyle = ReservedDanceStyle.ID), result.songs.single())
        assertEquals(choreo.copy(styleId = ReservedDanceStyle.ID,
            timeline = mapOf(1.5 to "${ReservedDanceStyle.ID}::m", 2.0 to "legacy")), result.choreographies.single())
        assertEquals(result, result.normalizeLocalStyles(catalog))
    }

    @Test fun duplicateMovePayloadCollisionRejectsRatherThanDropsOne() {
        val before = AppData(danceStyles = listOf(
            DanceStyle("deleted", "Old", listOf(Move("m", "A", ""))),
            DanceStyle(ReservedDanceStyle.ID, ReservedDanceStyle.NAME, listOf(Move("m", "B", ""))),
        ))
        assertFailsWith<IllegalArgumentException> { before.normalizeLocalStyles(catalog) }
    }

    @Test fun metadataOnlyStyleFieldsChangeAndGhostPayloadStaysPrivate() {
        val raw = """{"version":1,"custom":true,"items":[{"style":"deleted","snapshot":{"style":"Old","musicClips":[{"mediaUri":"own.wav","sourceStartMs":47}]}}]}"""
        val ghost = DanceStyle("__md_choreo_scoped__", "ChoreoScoped", listOf(Move("private", "Secret", "")))
        val result = AppData(danceStyles = listOf(ghost), settings = JsonObject(mapOf("editorVaultV1" to JsonPrimitive(raw))))
            .normalizeLocalStyles(catalog)
        assertEquals(ghost, result.danceStyles.single { it.id == ghost.id })
        assertTrue(result.danceStyles.first { it.id == ReservedDanceStyle.ID }.moves.isEmpty())
        val json = Json.parseToJsonElement(result.settings.getValue("editorVaultV1").jsonPrimitive.content).jsonObject
        assertEquals(JsonPrimitive(true), json["custom"])
        val item = json.getValue("items").jsonArray.single().jsonObject
        assertEquals(ReservedDanceStyle.ID, item.getValue("style").jsonPrimitive.content)
        assertEquals("own.wav", item.getValue("snapshot").jsonObject.getValue("musicClips").jsonArray.single().jsonObject.getValue("mediaUri").jsonPrimitive.content)
    }

    @Test fun brokenEditorMetadataIsAnErrorAndNotAnEmptyReplacement() {
        assertFails { AppData(settings = JsonObject(mapOf("editorVaultV1" to JsonPrimitive("broken")))).normalizeLocalStyles(catalog) }
    }

    @Test fun duplicateGlobalIdentityAcrossSurvivingStylesAndGhostRejectsEqualOrDifferentPayloads() {
        val live = catalog.copy(entries = catalog.entries + DanceStyleEntry("b", "Beta"))
        val move = Move("same", "Own", "")
        listOf("b", "__md_choreo_scoped__").forEach { secondOwner ->
            listOf(move, move.copy(name = "Different payload")).forEach { secondMove ->
                val data = AppData(danceStyles = listOf(
                    DanceStyle("fresh", "Old", listOf(move)),
                    DanceStyle(secondOwner, "Second", listOf(secondMove)),
                ))
                assertFailsWith<IllegalArgumentException> { data.normalizeLocalStyles(live) }
            }
        }
    }

    @Test fun roomProjectionRejectsFutureVersionAndScalarSnapshotButKeepsHistoricalPartialItems() {
        listOf("""{"version":99,"items":[{"style":"deleted"}]}""",
            """{"version":"1","items":[]}""",
            """{"version":1,"items":[{"style":"deleted","snapshot":42}]}""",
            """{"items":[{"style":99}]}""").forEach { raw ->
            assertFails { normalizeEditorVaultStyles(JsonObject(mapOf("editorVaultV1" to JsonPrimitive(raw))), catalog) }
        }
        val oldPartial = """{"items":[{"style":"deleted","future":true,"snapshot":{"style":"deleted","unknown":7}},{"snapshot":null}]}"""
        val transformed = normalizeEditorVaultStyles(JsonObject(mapOf("editorVaultV1" to JsonPrimitive(oldPartial))), catalog)
            .getValue("editorVaultV1").jsonPrimitive.content
        assertEquals(Json.parseToJsonElement(oldPartial.replace("\"deleted\"", "\"${ReservedDanceStyle.ID}\"")), Json.parseToJsonElement(transformed))
    }
}
