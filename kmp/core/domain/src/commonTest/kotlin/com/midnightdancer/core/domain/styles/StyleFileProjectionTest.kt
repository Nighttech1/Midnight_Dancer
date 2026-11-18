package com.midnightdancer.core.domain.styles

import kotlinx.serialization.json.*
import kotlin.test.*

class StyleFileProjectionTest {
    private val catalog = DanceStyleCatalogSnapshot(2,
        listOf(DanceStyleEntry(ReservedDanceStyle.ID, ReservedDanceStyle.NAME, true)), mapOf("a" to ReservedDanceStyle.ID))

    @Test fun vaultProjectionChangesOnlyStyleFieldsIncludingUnknownClipAndRootFields() {
        val raw = """{"version":1,"futureRoot":{"keep":9},"items":[{"id":"c","title":"Own","status":"Ready","style":"a","futureItem":true,"snapshot":{"title":"Own","style":"a","futureSnapshot":"keep","elementClips":[{"label":"A","durationMs":3000,"mediaUri":"own.mp4","futureClip":[1,2]}]}}]}"""
        assertEquals(raw, projectVaultStyleJson(raw))
        val expected = Json.parseToJsonElement(raw.replace("\"style\":\"a\"", "\"style\":\"${ReservedDanceStyle.ID}\""))
        assertEquals(expected, Json.parseToJsonElement(projectVaultStyleJson(raw, catalog)))
    }

    @Test fun invalidOrUnsupportedVaultIsRejectedWithoutProducingEmptyDocument() {
        listOf("broken", "{}", """{"version":2,"items":[]}""", """{"version":"1","items":[]}""",
            """{"version":null,"items":[]}""", """{"version":1,"items":[null]}""").forEach { raw ->
            assertFails { projectVaultStyleJson(raw, catalog) }
        }
    }

    @Test fun mineProjectionPreservesImportedAndUnknownMetadataExactly() {
        val raw = """{"schemaVersion":1,"futureRoot":[1,2],"sets":[{"id":"mine","name":"Own","styleId":"a","styleName":"Old","future":true},{"id":"import","origin":"Imported","styleId":"a","styleName":"Archive authentic","archivePath":"own.dance","futureImported":{"x":1}}]}"""
        val old = Json.parseToJsonElement(raw).jsonObject
        val next = Json.parseToJsonElement(projectSavedSetStyleJson(raw, catalog)).jsonObject
        assertEquals(old["futureRoot"], next["futureRoot"])
        assertEquals(old.getValue("sets").jsonArray[1], next.getValue("sets").jsonArray[1])
        assertEquals(JsonPrimitive(true), next.getValue("sets").jsonArray[0].jsonObject["future"])
        assertEquals(ReservedDanceStyle.ID, next.getValue("sets").jsonArray[0].jsonObject.getValue("styleId").jsonPrimitive.content)
        assertEquals(raw, projectSavedSetStyleJson(raw))
    }

    @Test fun unsupportedOrMalformedSetSchemaIsRejected() {
        listOf("bad", "{}", """{"schemaVersion":2,"sets":[]}""", """{"schemaVersion":1,"sets":[{"id":"x","origin":"Future"}]}""",
            """{"schemaVersion":1,"sets":[{"id":"x","styleId":99}]}""").forEach { raw ->
            assertFails { projectSavedSetStyleJson(raw, catalog) }
        }
    }
}
