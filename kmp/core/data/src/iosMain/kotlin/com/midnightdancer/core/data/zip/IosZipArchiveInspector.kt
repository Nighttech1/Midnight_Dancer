package com.midnightdancer.core.data.zip

/**
 * iOS Stage-2 stub — listing real ZIP bytes lands with C-17 goldens / Platform Ops.
 * Callers still get a clear failure (no silent accept).
 */
class IosZipArchiveInspector : ZipArchiveInspector {
    override fun listEntryPaths(absoluteArchivePath: String): Result<List<String>> =
        Result.failure(
            UnsupportedOperationException(
                "ZIP listing on iOS not wired in Stage 2; use Android inspector or FakeZipArchiveInspector in tests",
            ),
        )
}
