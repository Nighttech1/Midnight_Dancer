package com.midnightdancer.core.data.zip

import com.midnightdancer.core.domain.usecase.SecureZipValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SecureZipSandboxTest {
    private val sandbox = "/tmp/md-sandbox"
    private val whitelist = SecureZipWhitelist.DefaultUserSpec

    @Test
    fun allows_whitelisted_inside_sandbox() {
        assertTrue(
            SecureZipSandbox.isExtractTargetAllowed(sandbox, "videos/a.mp4", whitelist),
        )
        SecureZipSandbox.assertExtractTargetAllowed(sandbox, "metadata.json", whitelist)
    }

    @Test
    fun rejects_outside_sandbox_joins() {
        assertFalse(
            SecureZipSandbox.isExtractTargetAllowed(sandbox, "../outside.mp4", whitelist),
        )
        assertFailsWith<IllegalArgumentException> {
            SecureZipSandbox.assertExtractTargetAllowed(sandbox, "../escape.mp4", whitelist)
        }
        assertFalse(
            SecureZipSandbox.isExtractTargetAllowed(sandbox, "/abs/a.mp4", whitelist),
        )
        assertFalse(
            SecureZipSandbox.isExtractTargetAllowed(sandbox, "foo/../../etc/x.mp4", whitelist),
        )
    }

    @Test
    fun rejects_non_whitelisted_even_if_inside() {
        assertFalse(
            SecureZipSandbox.isExtractTargetAllowed(sandbox, "readme.txt", whitelist),
        )
    }
}

class SecureZipMetadataValidationTest {
    @Test
    fun rejects_oversized_metadata() {
        val huge = "x".repeat(SecureZipMetadataLimits.MAX_METADATA_JSON_BYTES + 1)
        val reasons = validateMetadataJsonBeforeDecode(huge)
        assertTrue(reasons.any { it.startsWith("metadata_too_large") })
    }

    @Test
    fun rejects_missing_top_level_keys() {
        val reasons = validateMetadataJsonBeforeDecode("""{"danceStyles":[]}""")
        assertTrue(reasons.any { it.startsWith("metadata_missing_field") })
    }

    @Test
    fun accepts_flutter_shaped_object() {
        val ok =
            """
            {"danceStyles":[],"songs":[],"choreographies":[],"settings":{}}
            """.trimIndent()
        assertEquals(emptyList(), validateMetadataJsonBeforeDecode(ok))
    }

    @Test
    fun rejects_non_object() {
        assertEquals(listOf("metadata_not_object"), validateMetadataJsonBeforeDecode("[]"))
    }
}

class SecureZipPreflightEntryCapTest {
    @Test
    fun validate_rejects_when_entry_count_exceeds_max() {
        val limits = SecureZipLimits(maxTotalEntries = 3)
        val r =
            DefaultValidateSecureZip.validateEntryPaths(
                entries = listOf("a.mp4", "b.mp4", "c.mp4", "d.mp4"),
                limits = limits,
            )
        val rejected = assertIs<SecureZipValidationResult.Rejected>(r)
        assertTrue(rejected.reasons.any { it.startsWith("too_many_entries") })
    }

    @Test
    fun preflight_ok_under_cap() {
        assertIs<SecureZipPreflightCheck.Ok>(
            DefaultSecureZipPreflight.checkListedEntryCount(10),
        )
    }

    @Test
    fun ten_percent_buffer_parity() {
        assertEquals(0L, SecureZipPreflightResult.withTenPercentBuffer(0))
        assertEquals(110L, SecureZipPreflightResult.withTenPercentBuffer(100))
    }
}