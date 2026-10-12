package com.midnightdancer.core.data.zip

/**
 * Lists ZIP member paths without extracting payloads.
 * Android: [java.util.zip.ZipFile]; iOS: Stage-2 stub until native zip wired (C-17 goldens).
 */
interface ZipArchiveInspector {
    fun listEntryPaths(absoluteArchivePath: String): Result<List<String>>
}

/** In-memory inspector for unit tests. */
class FakeZipArchiveInspector(
    private val pathsByArchive: Map<String, List<String>> = emptyMap(),
    private val defaultPaths: List<String> = emptyList(),
) : ZipArchiveInspector {
    override fun listEntryPaths(absoluteArchivePath: String): Result<List<String>> =
        Result.success(pathsByArchive[absoluteArchivePath] ?: defaultPaths)
}
