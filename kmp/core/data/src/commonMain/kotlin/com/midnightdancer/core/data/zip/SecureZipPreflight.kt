package com.midnightdancer.core.data.zip

/**
 * Flutter-parity preflight limits for secure ZIP import (STEP 1–2).
 *
 * Entry-count is enforced now via [DefaultValidateSecureZip].
 * Free-space comparison (expect/actual on extract volume + 10% buffer) is a
 * **TODO for Platform Ops** when byte extract is wired — see [SecureZipLimitsDefaults.FREE_SPACE_CHECK_TODO].
 */
data class SecureZipLimits(
    /** Max central-directory size in bytes (Flutter default 32 MiB). */
    val maxCentralDirectoryBytes: Int = SecureZipLimitsDefaults.MAX_CENTRAL_DIRECTORY_BYTES,
    /** Max total ZIP entries before reject (Flutter default 200_000). */
    val maxTotalEntries: Int = SecureZipLimitsDefaults.MAX_TOTAL_ENTRIES,
) {
    companion object {
        val FlutterParity: SecureZipLimits = SecureZipLimits()
    }
}

object SecureZipLimitsDefaults {
    const val MAX_CENTRAL_DIRECTORY_BYTES: Int = 32 * 1024 * 1024
    const val MAX_TOTAL_ENTRIES: Int = 200_000

    /**
     * Allowed compression methods when extract is ported: store (0) and deflate (8) only.
     * Encrypted / data-descriptor entries must be rejected (Flutter extract parity).
     */
    const val ALLOWED_COMPRESSION_METHODS_NOTE: String =
        "store(0) and deflate(8) only; reject encrypted and data-descriptor entries"

    /**
     * TODO(Platform Ops): expect/actual free-space on extract volume;
     * fail closed if free < whitelistUncompressedSum * 1.1 (Flutter STEP 2).
     */
    const val FREE_SPACE_CHECK_TODO: String =
        "Wire device free-space expect/actual before byte extract; compare to preflight buffer"
}

/**
 * Result shape for future preflight (CD sizes only — no decompress).
 * [whitelistUncompressedSumBytes] filled when inspectors expose sizes.
 */
data class SecureZipPreflightResult(
    val whitelistUncompressedSumBytes: Long = 0L,
    val requiredBytesWithTenPercentBuffer: Long = 0L,
    val whitelistedEntryCount: Int = 0,
    val totalZipEntries: Int = 0,
) {
    companion object {
        /** Integer ceil(sum * 1.1) — Flutter preflight parity. */
        fun withTenPercentBuffer(sumBytes: Long): Long {
            if (sumBytes <= 0L) return 0L
            val withBuffer = (sumBytes * 11 + 9) / 10
            return if (withBuffer < sumBytes) sumBytes else withBuffer
        }
    }
}

/**
 * Preflight surface (Stage 2: entry-count via listed paths; sizes/free-space later).
 */
interface SecureZipPreflight {
    fun checkListedEntryCount(
        entryCount: Int,
        limits: SecureZipLimits = SecureZipLimits.FlutterParity,
    ): SecureZipPreflightCheck
}

sealed class SecureZipPreflightCheck {
    data object Ok : SecureZipPreflightCheck()

    data class Rejected(
        val reasons: List<String>,
    ) : SecureZipPreflightCheck()
}

object DefaultSecureZipPreflight : SecureZipPreflight {
    override fun checkListedEntryCount(
        entryCount: Int,
        limits: SecureZipLimits,
    ): SecureZipPreflightCheck {
        if (entryCount > limits.maxTotalEntries) {
            return SecureZipPreflightCheck.Rejected(
                listOf("too_many_entries:$entryCount"),
            )
        }
        return SecureZipPreflightCheck.Ok
    }
}