package com.midnightdancer.core.data.exchange

import android.content.ContentResolver
import android.net.Uri
import android.util.AtomicFile
import com.midnightdancer.core.data.zip.SecureZipLimitsDefaults
import com.midnightdancer.core.data.zip.SecureZipMetadataLimits
import com.midnightdancer.core.data.zip.SecureZipSandbox
import com.midnightdancer.core.data.zip.SecureZipWhitelist
import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.storage.DiskSpaceGate
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipFile

/** All user-controlled bytes are bounded and staged before catalog/vault writes. */
internal class AndroidExchangeArchiveFiles(
    private val root: File,
    private val storage: DeviceStorage,
    private val resolver: ContentResolver?,
) {
    fun readMetadataForPeek(archive: File): String? = ZipFile(archive).use { zip ->
        var count = 0
        var metadata: java.util.zip.ZipEntry? = null
        val seen = mutableSetOf<String>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            require(++count <= SecureZipLimitsDefaults.MAX_TOTAL_ENTRIES) { "too_many_entries" }
            if (entry.isDirectory) continue
            val path = entry.name.replace('\\', '/').trim()
            SecureZipSandbox.assertExtractTargetAllowed(root.absolutePath, path, SecureZipWhitelist.MidnightDancerFullBackup)
            require(seen.add(path)) { "duplicate_archive_entry" }
            if (path == "metadata.json") metadata = entry
        }
        metadata?.let { entry ->
            require(entry.size in 0..SecureZipMetadataLimits.MAX_METADATA_JSON_BYTES.toLong()) { "metadata_too_large" }
            val bytes = java.io.ByteArrayOutputStream()
            zip.getInputStream(entry).use { copyBounded(it, bytes, SecureZipMetadataLimits.MAX_METADATA_JSON_BYTES.toLong()) }
            bytes.toString(Charsets.UTF_8.name())
        }
    }

    fun copyContentArchive(uri: String): File {
        val content = requireNotNull(resolver) { "content_resolver_unavailable" }
        val dir = File(root, "exchange/picked").apply { check(mkdirs() || isDirectory) }
        val target = AtomicFile(File(dir, "${UUID.randomUUID()}.dance"))
        val out = target.startWrite()
        try {
            val input = requireNotNull(content.openInputStream(Uri.parse(uri))) { "cannot_open_selected_archive" }
            input.use { copyBounded(it, out, ExchangeZipLimits.MAX_ARCHIVE_BYTES) }
            target.finishWrite(out)
        } catch (error: Throwable) {
            target.failWrite(out)
            throw error
        }
        require(target.baseFile.length() > 0L) { "empty_selected_archive" }
        return target.baseFile
    }

    fun stage(zipFile: File): StagedMedia {
        val token = UUID.randomUUID().toString()
        val directory = File(root, "exchange/staging/$token")
        check(directory.mkdirs() || directory.isDirectory)
        try {
            val files = linkedMapOf<String, File>()
            var total = 0L
            ZipFile(zipFile).use { zip ->
                val entries = ArrayList<java.util.zip.ZipEntry>()
                val enumeration = zip.entries()
                var entryCount = 0
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    require(++entryCount <= SecureZipLimitsDefaults.MAX_TOTAL_ENTRIES) { "too_many_entries" }
                    if (!entry.isDirectory) entries += entry
                }
                val paths = mutableSetOf<String>()
                var advertisedBytes = 0L
                entries.forEach { entry ->
                    val path = entry.name.replace('\\', '/').trim()
                    SecureZipSandbox.assertExtractTargetAllowed(directory.absolutePath, path, SecureZipWhitelist.MidnightDancerFullBackup)
                    require(paths.add(path)) { "duplicate_archive_entry:$path" }
                    require(entry.method == 0 || entry.method == 8) { "unsupported_compression" }
                    require(entry.size >= 0L && entry.size <= ExchangeZipLimits.MAX_MEDIA_BYTES) { "invalid_entry_size" }
                    if (path == "metadata.json") {
                        require(entry.size <= SecureZipMetadataLimits.MAX_METADATA_JSON_BYTES) { "metadata_too_large" }
                    }
                    advertisedBytes += entry.size
                    require(advertisedBytes <= ExchangeZipLimits.MAX_MEDIA_BYTES) { "uncompressed_package_too_large" }
                }
                require(DiskSpaceGate.canImport(storage.freeBytes(), advertisedBytes)) { "insufficient_free_space_for_extract" }
                entries.forEach { entry ->
                    val path = entry.name.replace('\\', '/').trim()
                    val file = File(directory, path)
                    check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
                    val cap = if (path == "metadata.json") SecureZipMetadataLimits.MAX_METADATA_JSON_BYTES.toLong()
                        else ExchangeZipLimits.MAX_MEDIA_BYTES - total
                    val written = zip.getInputStream(entry).use { input ->
                        file.outputStream().use { output -> copyBounded(input, output, cap) }
                    }
                    require(written == entry.size) { "entry_size_mismatch:$path" }
                    if (path.substringAfter('/').startsWith("editor-")) {
                        val expected = Regex("(?:videos|music)/editor-([a-f0-9]{64})\\.(?:mp4|mov|mp3|m4a|wav|aac|ogg|flac)")
                            .matchEntire(path)?.groupValues?.get(1)
                        require(expected != null) { "invalid_editor_media_filename" }
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        file.inputStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                digest.update(buffer, 0, count)
                            }
                        }
                        val actual = digest.digest().joinToString("") { "%02x".format(it) }
                        require(actual == expected) { "editor_media_hash_mismatch:$path" }
                    }
                    total += written
                    require(total <= ExchangeZipLimits.MAX_MEDIA_BYTES) { "uncompressed_package_too_large" }
                    if (path.startsWith("videos/") || path.startsWith("music/")) files[path] = file
                }
            }
            return StagedMedia(root, directory, token, files)
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }

    private fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "archive_bytes_limit_exceeded" }
            output.write(buffer, 0, count)
        }
        return total
    }
}

internal class StagedMedia(
    private val root: File,
    private val directory: File,
    token: String,
    private val files: Map<String, File>,
) {
    private val created = mutableListOf<File>()
    private val targets = files.mapValues { (relative, _) ->
        // Editor media has no Room identity and gets its own immutable import directory.
        val targetRelative = if (relative.substringAfter('/').startsWith("editor-")) {
            "exchange/imported_media/$token/$relative"
        } else relative
        val target = File(root, targetRelative)
        require(target.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "outside_media_root" }
        target
    }
    val resolvedPaths: Map<String, String> = targets.mapValues { it.value.absolutePath }

    init {
        files.forEach { (relative, staged) ->
            val target = targets.getValue(relative)
            require(!target.exists() || (target.isFile && sameContent(staged, target))) {
                "media_conflict:$relative: existing media differs; original preserved"
            }
        }
    }

    fun commit() {
        files.forEach { (relative, staged) ->
            val target = targets.getValue(relative)
            if (target.exists()) {
                require(target.isFile && sameContent(staged, target)) { "media_conflict:$relative" }
            } else {
                check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
                check(staged.renameTo(target)) { "cannot_commit_import_media:$relative" }
                created += target
            }
        }
    }

    fun rollback() {
        created.asReversed().forEach { it.delete() }
        created.clear()
    }

    fun close() { directory.deleteRecursively() }

    private fun sameContent(left: File, right: File): Boolean {
        if (left.length() != right.length()) return false
        fun hash(file: File): ByteArray {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest()
        }
        return hash(left).contentEquals(hash(right))
    }
}
