package com.midnightdancer.core.data.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.midnightdancer.core.domain.usecase.SecureZipValidationResult

class SecureZipWhitelistTest {
    @Test
    fun default_allows_video_image_metadata() {
        val w = SecureZipWhitelist.DefaultUserSpec
        assertTrue(w.matchesEntryPath("videos/a.mp4"))
        assertTrue(w.matchesEntryPath("x/y.MOV"))
        assertTrue(w.matchesEntryPath("cover.jpg"))
        assertTrue(w.matchesEntryPath("metadata.json"))
        assertFalse(w.matchesEntryPath("track.mp3"))
        assertFalse(w.matchesEntryPath("manifest.json"))
        assertFalse(w.matchesEntryPath("readme.txt"))
        assertFalse(w.matchesEntryPath("videos/"))
    }

    @Test
    fun full_backup_allows_audio_and_manifest() {
        val w = SecureZipWhitelist.MidnightDancerFullBackup
        assertTrue(w.matchesEntryPath("music/s.mp3"))
        assertTrue(w.matchesEntryPath("a.m4a"))
        assertTrue(w.matchesEntryPath("b.wav"))
        assertTrue(w.matchesEntryPath("manifest.json"))
    }
}

class ZipPathSafetyTest {
    @Test
    fun rejects_traversal_and_absolute() {
        assertTrue(entryPathFailsZipSlipChecks("../etc/passwd"))
        assertTrue(entryPathFailsZipSlipChecks("/abs/file.mp4"))
        assertTrue(entryPathFailsZipSlipChecks("C:/windows/x"))
        assertTrue(entryPathFailsZipSlipChecks("foo/../../bar"))
        assertFalse(entryPathFailsZipSlipChecks("videos/a.mp4"))
        assertFalse(entryPathFailsZipSlipChecks("metadata.json"))
    }

    @Test
    fun sandbox_join_stays_inside() {
        assertTrue(isInsideSandbox("/tmp/sandbox", "music/a.mp3"))
        assertFalse(isInsideSandbox("/tmp/sandbox", "../outside"))
        assertFalse(isInsideSandbox("/tmp/sandbox", "/abs"))
    }
}

class DefaultValidateSecureZipTest {
    @Test
    fun ok_when_all_entries_allowed() {
        val r =
            DefaultValidateSecureZip.validateEntryPaths(
                listOf("metadata.json", "videos/m1.mp4"),
            )
        assertIs<SecureZipValidationResult.Ok>(r)
    }

    @Test
    fun rejects_zip_slip_even_if_whitelist_ok_name() {
        val r =
            DefaultValidateSecureZip.validateEntryPaths(
                listOf("../metadata.json", "videos/ok.mp4"),
            )
        val rejected = assertIs<SecureZipValidationResult.Rejected>(r)
        assertTrue(rejected.reasons.any { it.startsWith("zip_slip:") })
    }

    @Test
    fun rejects_unknown_extension() {
        val r =
            DefaultValidateSecureZip.validateEntryPaths(
                listOf("evil.exe", "metadata.json"),
            )
        val rejected = assertIs<SecureZipValidationResult.Rejected>(r)
        assertTrue(rejected.reasons.any { it.startsWith("not_whitelisted:") })
    }
}
