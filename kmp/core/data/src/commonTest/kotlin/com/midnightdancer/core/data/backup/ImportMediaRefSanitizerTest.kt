package com.midnightdancer.core.data.backup

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.entity.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportMediaRefSanitizerTest {
    @Test
    fun nulls_dangerous_video_uri_schemes_and_absolute() {
        assertNull(ImportMediaRefSanitizer.sanitizeVideoUri("content://media/1"))
        assertNull(ImportMediaRefSanitizer.sanitizeVideoUri("file:///tmp/x.mp4"))
        assertNull(ImportMediaRefSanitizer.sanitizeVideoUri("http://evil/x.mp4"))
        assertNull(ImportMediaRefSanitizer.sanitizeVideoUri("https://evil/x.mp4"))
        assertNull(ImportMediaRefSanitizer.sanitizeVideoUri("/abs/path.mp4"))
        assertNull(ImportMediaRefSanitizer.sanitizeVideoUri("../escape.mp4"))
        assertEquals("m1.mp4", ImportMediaRefSanitizer.sanitizeVideoUri("m1.mp4"))
        assertEquals("clip.mp4", ImportMediaRefSanitizer.sanitizeVideoUri("videos/clip.mp4"))
    }

    @Test
    fun fileName_keeps_relative_basename_only() {
        assertEquals("a.mp3", ImportMediaRefSanitizer.sanitizeFileName("a.mp3"))
        assertEquals("track.mp3", ImportMediaRefSanitizer.sanitizeFileName("music/track.mp3"))
        assertEquals("x.mp3", ImportMediaRefSanitizer.sanitizeFileName("/abs/x.mp3"))
        assertTrue(ImportMediaRefSanitizer.sanitizeFileName("content://host/path").isNotBlank())
    }

    @Test
    fun merge_strips_dangerous_refs_from_imported_moves_and_songs() {
        val local = AppData()
        val imported =
            AppData(
                danceStyles =
                    listOf(
                        DanceStyle(
                            id = "st1",
                            name = "S",
                            moves =
                                listOf(
                                    Move(
                                        id = "m1",
                                        name = "A",
                                        level = "b",
                                        videoUri = "content://bad",
                                    ),
                                    Move(
                                        id = "m2",
                                        name = "B",
                                        level = "b",
                                        videoUri = "safe.mp4",
                                    ),
                                ),
                        ),
                    ),
                songs =
                    listOf(
                        Song(
                            id = "s1",
                            title = "T",
                            danceStyle = "S",
                            level = "b",
                            fileName = "/abs/evil.mp3",
                            duration = 1.0,
                            sizeBytes = 1,
                        ),
                    ),
            )
        val plan = FullBackupImportMerge.mergeInto(local, imported)
        val moves = plan.merged.danceStyles.single().moves
        assertNull(moves.first { it.id == "m1" }.videoUri)
        assertEquals("safe.mp4", moves.first { it.id == "m2" }.videoUri)
        assertEquals("evil.mp3", plan.merged.songs.single().fileName)
    }
}