package com.midnightdancer.core.domain

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.Choreography
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.entity.Song
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.settings.DanceReminderConfig
import com.midnightdancer.core.domain.settings.DanceReminderMode
import com.midnightdancer.core.domain.settings.LastImportManifest
import com.midnightdancer.core.domain.settings.LegalTermsConsent
import com.midnightdancer.core.domain.settings.SettingsKeys
import com.midnightdancer.core.domain.settings.UiLanguage
import com.midnightdancer.core.domain.timeline.ChoreographyTimelineRef
import com.midnightdancer.core.domain.validation.DomainValidators
import com.midnightdancer.core.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DomainSmokeTest {
    @Test
    fun domainMarker_isPresent_fullProductOnly() {
        assertEquals("core-domain", DomainMarker.NAME)
        assertEquals("full", DomainMarker.PRODUCT)
    }
}

class AppDataSerializationTest {
    @Test
    fun minimalAppData_roundTrips() {
        val move =
            Move(
                id = "m1",
                name = "Basic",
                level = "beginner",
                description = "step",
                videoUri = "m1",
                masteryPercent = 40,
            )
        val style =
            DanceStyle(
                id = "s1",
                name = "Salsa",
                moves = listOf(move),
                currentMoveId = "m1",
            )
        val song =
            Song(
                id = "song1",
                title = "Track",
                danceStyle = "Salsa",
                level = "beginner",
                fileName = "track.mp3",
                duration = 180.5,
                sizeBytes = 1024,
                playbackSpeed = 1.0,
            )
        val choreo =
            Choreography(
                id = "c1",
                name = "Routine",
                songId = "song1",
                styleId = "s1",
                timeline =
                    mapOf(
                        0.0 to ChoreographyTimelineRef.encode("s1", "m1"),
                        1.5 to "m1",
                    ),
                startTime = 0.0,
                endTime = 60.0,
            )
        var settings = JsonObject(emptyMap())
        settings = UiLanguage.embed(settings, UiLanguage.EN)
        settings = LegalTermsConsent.embedAccepted(settings)
        settings =
            DanceReminderConfig.embed(
                settings,
                DanceReminderConfig(
                    enabled = true,
                    hour = 20,
                    minute = 30,
                    mode = DanceReminderMode.weekly,
                    weekday = 5,
                ),
            )
        settings =
            LastImportManifest.embed(
                settings,
                LastImportManifest(
                    wholeStyleIds = setOf("s1"),
                    moveIds = setOf("m1"),
                    songIds = setOf("song1"),
                    choreographyIds = setOf("c1"),
                ),
            )

        val original =
            AppData(
                danceStyles = listOf(style),
                songs = listOf(song),
                choreographies = listOf(choreo),
                settings = settings,
            )

        val encoded = DomainJson.encodeAppData(original)
        val decoded = DomainJson.decodeAppData(encoded)

        assertEquals(original.danceStyles, decoded.danceStyles)
        assertEquals(original.songs, decoded.songs)
        assertEquals(original.choreographies, decoded.choreographies)
        assertEquals(UiLanguage.EN, UiLanguage.fromSettings(decoded.settings))
        assertTrue(LegalTermsConsent.isAccepted(decoded.settings))
        assertEquals(
            DanceReminderConfig(true, 20, 30, DanceReminderMode.weekly, 5),
            DanceReminderConfig.fromSettings(decoded.settings),
        )
        assertEquals(
            setOf("s1"),
            LastImportManifest.fromSettings(decoded.settings).wholeStyleIds,
        )
        assertTrue(encoded.contains("\"ui_language\""))
        assertTrue(encoded.contains("\"legalTermsAccepted\""))
        assertTrue(encoded.contains("\"last_import_manifest\""))
    }

    @Test
    fun move_readsLegacyVideoFileNameAndVideoRef() {
        val fromFileName =
            DomainJson.format.decodeFromString(
                Move.serializer(),
                """{"id":"a","name":"A","level":"l","videoFileName":"legacy.mp4"}""",
            )
        assertEquals("legacy.mp4", fromFileName.videoUri)

        val fromRef =
            DomainJson.format.decodeFromString(
                Move.serializer(),
                """{"id":"b","name":"B","level":"l","videoRef":"ref-id"}""",
            )
        assertEquals("ref-id", fromRef.videoUri)

        val prefersUri =
            DomainJson.format.decodeFromString(
                Move.serializer(),
                """{"id":"c","name":"C","level":"l","videoUri":"primary","videoFileName":"ignored"}""",
            )
        assertEquals("primary", prefersUri.videoUri)
    }

    @Test
    fun choreography_timeline_stringKeysRoundTrip() {
        val raw =
            """
            {
              "id":"c1","name":"N","songId":"s","styleId":"st",
              "timeline":{"0.0":"st::m1","2.5":"m1"},
              "startTime":0.0,"endTime":10.0
            }
            """.trimIndent()
        val c = DomainJson.format.decodeFromString(Choreography.serializer(), raw)
        assertEquals("st::m1", c.timeline[0.0])
        assertEquals("m1", c.timeline[2.5])
        val again = DomainJson.format.decodeFromString(
            Choreography.serializer(),
            DomainJson.format.encodeToString(Choreography.serializer(), c),
        )
        assertEquals(c.timeline, again.timeline)
    }
}

class DomainValidationTest {
    @Test
    fun playbackSpeed_acceptReject() {
        assertTrue(DomainValidators.isPlaybackSpeedValid(0.4))
        assertTrue(DomainValidators.isPlaybackSpeedValid(2.0))
        assertTrue(DomainValidators.isPlaybackSpeedValid(1.0))
        assertFalse(DomainValidators.isPlaybackSpeedValid(0.39))
        assertFalse(DomainValidators.isPlaybackSpeedValid(2.01))
        assertTrue(DomainValidators.isChoreoPlaybackSpeedValid(0.5))
        assertTrue(DomainValidators.isChoreoPlaybackSpeedValid(2.0))
        assertFalse(DomainValidators.isChoreoPlaybackSpeedValid(0.49))

        val bad =
            Song(
                id = "1",
                title = "t",
                danceStyle = "s",
                level = "l",
                fileName = "f.mp3",
                duration = 1.0,
                sizeBytes = 1,
                playbackSpeed = 0.2,
            )
        assertIs<ValidationResult.Err>(DomainValidators.validateSong(bad))

        val good = bad.copy(playbackSpeed = 0.8)
        assertIs<ValidationResult.Ok>(DomainValidators.validateSong(good))
        val atMax = bad.copy(playbackSpeed = 2.0)
        assertIs<ValidationResult.Ok>(DomainValidators.validateSong(atMax))
    }

    @Test
    fun masteryPercent_acceptReject() {
        assertTrue(DomainValidators.isMasteryPercentValid(0))
        assertTrue(DomainValidators.isMasteryPercentValid(100))
        assertFalse(DomainValidators.isMasteryPercentValid(-1))
        assertFalse(DomainValidators.isMasteryPercentValid(101))

        val bad = Move(id = "m", name = "n", level = "l", masteryPercent = 150)
        assertIs<ValidationResult.Err>(DomainValidators.validateMove(bad))
        assertIs<ValidationResult.Ok>(
            DomainValidators.validateMove(bad.copy(masteryPercent = 50)),
        )
    }

    @Test
    fun timelineRefs_rejectEmpty() {
        val bad =
            Choreography(
                id = "c",
                name = "n",
                songId = "s",
                styleId = "st",
                timeline = mapOf(0.0 to "", 1.0 to "::"),
                endTime = 5.0,
            )
        val result = DomainValidators.validateChoreography(bad)
        assertIs<ValidationResult.Err>(result)

        val good =
            bad.copy(
                timeline =
                    mapOf(
                        0.0 to ChoreographyTimelineRef.encode("st", "m1"),
                        1.0 to "m1",
                    ),
            )
        assertIs<ValidationResult.Ok>(DomainValidators.validateChoreography(good))
    }

    @Test
    fun blankIds_rejected() {
        assertIs<ValidationResult.Err>(
            DomainValidators.validateMove(Move(id = " ", name = "n", level = "l")),
        )
        assertIs<ValidationResult.Err>(
            DomainValidators.validateDanceStyle(DanceStyle(id = "", name = "Salsa")),
        )
    }
}

class TimelineRefTest {
    @Test
    fun encodeDecodeAndResolve() {
        val move = Move(id = "m1", name = "Basic", level = "b")
        val other = Move(id = "m2", name = "Spin", level = "b")
        val styles =
            listOf(
                DanceStyle(id = "s1", name = "A", moves = listOf(move)),
                DanceStyle(id = "s2", name = "B", moves = listOf(other)),
            )
        val encoded = ChoreographyTimelineRef.encode("s2", "m2")
        assertEquals(ChoreographyTimelineRef.Parts("s2", "m2"), ChoreographyTimelineRef.decode(encoded))
        assertEquals(other, ChoreographyTimelineRef.resolveMove(styles, "s1", encoded))
        assertEquals(move, ChoreographyTimelineRef.resolveMove(styles, "s1", "Basic"))
        assertNull(ChoreographyTimelineRef.resolveMove(styles, "s1", ""))
        assertFalse(ChoreographyTimelineRef.isValidRef(""))
        assertFalse(ChoreographyTimelineRef.isValidRef("::"))
        assertTrue(ChoreographyTimelineRef.isValidRef("s1::m1"))
    }
}

class SettingsValueTypesTest {
    @Test
    fun settingsKeys_matchFlutter() {
        assertEquals("ui_language", SettingsKeys.UI_LANGUAGE)
        assertEquals("legalTermsAccepted", SettingsKeys.LEGAL_TERMS_ACCEPTED)
        assertEquals("danceReminderEnabled", SettingsKeys.DANCE_REMINDER_ENABLED)
        assertEquals("last_import_manifest", SettingsKeys.LAST_IMPORT_MANIFEST)
    }

    @Test
    fun uiLanguage_defaultsToRu_fullProduct() {
        assertEquals(UiLanguage.RU, UiLanguage.fromCode(null))
        assertEquals(UiLanguage.RU, UiLanguage.fromSettings(JsonObject(emptyMap())))
        assertEquals(UiLanguage.ES, UiLanguage.fromCode("es"))
    }

    @Test
    fun lastImportManifest_embedAndParse() {
        val manifest =
            LastImportManifest(
                wholeStyleIds = setOf("a"),
                moveIds = emptySet(),
                songIds = setOf("song"),
                choreographyIds = emptySet(),
            )
        val settings = LastImportManifest.embed(JsonObject(emptyMap()), manifest)
        assertNotNull(settings[SettingsKeys.LAST_IMPORT_MANIFEST]?.jsonObject)
        val parsed = LastImportManifest.fromSettings(settings)
        assertEquals(setOf("a"), parsed.wholeStyleIds)
        assertEquals(setOf("song"), parsed.songIds)

        val cleared = LastImportManifest.embed(settings, LastImportManifest.empty())
        assertNull(cleared[SettingsKeys.LAST_IMPORT_MANIFEST])
    }

    @Test
    fun legalAndReminder_roundTripInSettings() {
        var s = LegalTermsConsent.embedAccepted(JsonObject(emptyMap()))
        assertEquals(true, s[SettingsKeys.LEGAL_TERMS_ACCEPTED]?.jsonPrimitive?.booleanOrNull)
        s =
            DanceReminderConfig.embed(
                s,
                DanceReminderConfig(enabled = true, hour = 9, minute = 15, mode = DanceReminderMode.weekdays),
            )
        assertEquals("weekdays", s[SettingsKeys.DANCE_REMINDER_MODE]?.jsonPrimitive?.contentOrNull)
        assertIs<ValidationResult.Ok>(
            DomainValidators.validateReminder(DanceReminderConfig.fromSettings(s)),
        )
        assertIs<ValidationResult.Err>(
            DomainValidators.validateReminder(DanceReminderConfig(hour = 99)),
        )
    }
}
