package com.midnightdancer.core.data.zip

/** Real bounded ZIP32 preflight; encrypted MDANCE must be authenticated by the container port first. */
class IosZipArchiveInspector(private val archive: IosSecureZipArchive = IosSecureZipArchive()) : ZipArchiveInspector {
    override fun listEntryPaths(absoluteArchivePath: String): Result<List<String>> =
        runCatching { archive.inspect(absoluteArchivePath).map { it.name } }
}
