package com.midnightdancer.core.data.zip

import java.util.zip.ZipFile

/** Android [ZipArchiveInspector] via JDK ZipFile (list names only). */
class AndroidZipArchiveInspector : ZipArchiveInspector {
    override fun listEntryPaths(absoluteArchivePath: String): Result<List<String>> =
        runCatching {
            ZipFile(absoluteArchivePath).use { zip ->
                zip.entries().asSequence().map { it.name }.toList()
            }
        }
}
