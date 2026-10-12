package com.midnightdancer.core.data.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * C-14 path helpers — fake documents root (no platform documents / IO).
 */
class AppFilesPathTest {
    private val docs = "/data/user/0/com.midnightdancer.app/files"
    private val files = AppFiles(docs)

    @Test
    fun root_music_videos_metadata_match_flutter_layout() {
        assertEquals("$docs/MidnightDancer", files.rootDirectory())
        assertEquals("$docs/MidnightDancer/music", files.musicDirectory())
        assertEquals("$docs/MidnightDancer/videos", files.videosDirectory())
        assertEquals("$docs/MidnightDancer/metadata.json", files.metadataFilePath())
    }

    @Test
    fun mediaFilePath_music_default_and_custom_extension() {
        assertEquals(
            "$docs/MidnightDancer/music/song1.mp3",
            files.mediaFilePath("song1", MediaFileKind.MUSIC),
        )
        assertEquals(
            "$docs/MidnightDancer/music/song1.m4a",
            files.mediaFilePath("song1", MediaFileKind.MUSIC, "m4a"),
        )
        assertEquals(
            "$docs/MidnightDancer/music/song1.wav",
            files.mediaFilePath("song1", MediaFileKind.MUSIC, ".wav"),
        )
    }

    @Test
    fun mediaFilePath_video_is_mp4() {
        assertEquals(
            "$docs/MidnightDancer/videos/move-basic.mp4",
            files.mediaFilePath("move-basic", MediaFileKind.VIDEO),
        )
    }

    @Test
    fun mediaFilePath_rejects_traversal_and_separators_in_id() {
        assertFailsWith<IllegalArgumentException> {
            files.mediaFilePath("../escape", MediaFileKind.VIDEO)
        }
        assertFailsWith<IllegalArgumentException> {
            files.mediaFilePath("a/b", MediaFileKind.MUSIC)
        }
        assertFailsWith<IllegalArgumentException> {
            files.mediaFilePath("a\\b", MediaFileKind.MUSIC)
        }
        assertFailsWith<IllegalArgumentException> {
            files.mediaFilePath("", MediaFileKind.MUSIC)
        }
        assertFailsWith<IllegalArgumentException> {
            files.mediaFilePath("bad id!", MediaFileKind.MUSIC)
        }
    }

    @Test
    fun mediaFilePath_rejects_non_allowlisted_music_extension() {
        assertFailsWith<IllegalArgumentException> {
            files.mediaFilePath("song1", MediaFileKind.MUSIC, "exe")
        }
        assertFailsWith<IllegalArgumentException> {
            files.mediaFilePath("song1", MediaFileKind.MUSIC, "../x")
        }
    }

    @Test
    fun mediaFilePath_stays_under_music_or_videos_prefix() {
        val music = files.mediaFilePath("track_1", MediaFileKind.MUSIC)
        assertTrue(music.startsWith(files.musicDirectory() + "/"))
        val video = files.mediaFilePath("move_1", MediaFileKind.VIDEO)
        assertTrue(video.startsWith(files.videosDirectory() + "/"))
    }

    @Test
    fun mediaFileKind_fromFlutterType() {
        assertEquals(MediaFileKind.MUSIC, MediaFileKind.fromFlutterType("music"))
        assertEquals(MediaFileKind.VIDEO, MediaFileKind.fromFlutterType("video"))
        assertFailsWith<IllegalStateException> { MediaFileKind.fromFlutterType("photo") }
    }

    @Test
    fun layout_constants_locked() {
        assertEquals("MidnightDancer", AppFileLayout.ROOT_FOLDER)
        assertEquals("com.midnightdancer.app", AppFileLayout.APPLICATION_ID)
        assertEquals("metadata.json", AppFileLayout.METADATA_FILE)
    }

    @Test
    fun ensureLayout_uses_fake_fs() {
        val created = mutableListOf<String>()
        val storage =
            DefaultMediaStorage(
                documentsPath = docs,
                fileSystem =
                    object : FileSystemOps {
                        override fun ensureDirectory(absolutePath: String) {
                            created += absolutePath
                        }
                    },
            )
        storage.ensureLayout()
        assertEquals(3, created.size)
        assertTrue(created[0].endsWith("/MidnightDancer"))
        assertTrue(created[1].endsWith("/MidnightDancer/music"))
        assertTrue(created[2].endsWith("/MidnightDancer/videos"))
        assertTrue(storage.rootDirectory().isNotEmpty())
    }
}