package com.midnightdancer.core.data.zip

import java.util.zip.ZipFile

/**
 * DEBUG / Stage-2 reader: pulls `metadata.json` via [ZipFile] after path validation.
 * Release extract should prefer sandbox-extracted bytes (see [SecureZipMetadataReader] docs).
 */
class AndroidZipMetadataReader : SecureZipMetadataReader {
    override fun readMetadataJson(absoluteArchivePath: String): String? =
        runCatching {
            ZipFile(absoluteArchivePath).use { zip ->
                val entry =
                    zip.entries().asSequence().firstOrNull { e ->
                        !e.isDirectory &&
                            e.name.replace('\\', '/').trim().trimStart('/') == "metadata.json"
                    } ?: return@use null
                zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        }.getOrNull()
}
