package com.midnightdancer.core.data.zip

import com.midnightdancer.core.data.files.platformDocumentsDirectoryPath
import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.storage.DiskSpaceGate
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use
import platform.Foundation.NSUUID

/** ZIP32 only; names are validated before Okio may normalize them. No ZIP encryption or ZIP64. */
data class IosZipMember(val name: String, val size: Long, val compressedSize: Long,
    val crc32: Long, val method: Int, val flags: Int, val localOffset: Long, val directory: Boolean)
data class IosZipSource(val archivePath: String, val sourcePath: String)

/** Concrete reusable platform I/O, not an installed ExchangeZipUseCase or .mdance codec. */
@OptIn(ExperimentalForeignApi::class)
class IosSecureZipArchive(privateRoot: String = platformDocumentsDirectoryPath()) {
    private val fs = FileSystem.SYSTEM
    private val root = fs.canonicalize(privateRoot.toPath())

    fun inspect(archivePath: String): List<IosZipMember> {
        val path = archivePath.toPath()
        val length = fs.metadata(path).size ?: error("zip_unknown_size")
        require(length in 22..MAX_ARCHIVE_BYTES) { "zip_size_limit" }
        fs.openReadOnly(path).use { file ->
            val tailSize = minOf(length, 65557L)
            val tail = file.source(length - tailSize).buffer().use { it.readByteArray(tailSize) }
            var end = tail.size - 22
            while (end >= 0 && !(u32(tail, end) == 0x06054b50L && end + 22 + u16(tail, end + 20) == tail.size)) end--
            require(end >= 0) { "zip_end_missing" }
            require(u16(tail, end + 4) == 0 && u16(tail, end + 6) == 0) { "zip_multidisk_unsupported" }
            val count = u16(tail, end + 10)
            require(count in 1..65534 && count == u16(tail, end + 8)) { "zip_entry_count" }
            val centralSize = u32(tail, end + 12)
            val centralOffset = u32(tail, end + 16)
            require(centralSize <= 32L * 1024 * 1024 && centralOffset + centralSize == length - tailSize + end) { "zip_central_bounds" }
            val entries = mutableListOf<IosZipMember>()
            val seen = mutableSetOf<String>()
            var total = 0L
            file.source(centralOffset).buffer().use { input ->
                val central = Buffer().write(input.readByteArray(centralSize))
                repeat(count) {
                    require(central.size >= 46 && central.readIntLe() == 0x02014b50) { "zip_central_header" }
                    central.skip(4) // made-by + needed versions
                    val flags = central.readShortLe().toInt() and 65535
                    val method = central.readShortLe().toInt() and 65535
                    central.skip(4) // DOS date/time
                    val crc = central.readIntLe().toLong() and 0xffffffffL
                    val compressed = central.readIntLe().toLong() and 0xffffffffL
                    val size = central.readIntLe().toLong() and 0xffffffffL
                    val nameSize = central.readShortLe().toInt() and 65535
                    val extraSize = central.readShortLe().toInt() and 65535
                    val commentSize = central.readShortLe().toInt() and 65535
                    require(central.readShortLe().toInt() == 0) { "zip_multidisk_unsupported" }
                    central.skip(2)
                    val attributes = central.readIntLe().toLong() and 0xffffffffL
                    val offset = central.readIntLe().toLong() and 0xffffffffL
                    require(nameSize in 1..4096 && nameSize.toLong() + extraSize + commentSize <= central.size) { "zip_name_bounds" }
                    val name = central.readByteArray(nameSize.toLong()).decodeToString(throwOnInvalidSequence = true)
                    central.skip(extraSize.toLong() + commentSize)
                    require(flags and 0xF7F7 == 0 && method in listOf(0, 8)) { "zip_unsupported_flags_or_method" } // UTF8 + descriptor only
                    require((attributes ushr 16) and 0xf000 != 0xa000L) { "zip_symlink" }
                    val directory = name.endsWith('/')
                    validateName(name, directory)
                    require(seen.add(name.removeSuffix("/"))) { "zip_duplicate_name" }
                    require(size <= MAX_ARCHIVE_BYTES && compressed <= MAX_ARCHIVE_BYTES && offset < centralOffset) { "zip_member_bounds" }
                    require(method != 0 || size == compressed) { "zip_stored_size_mismatch" }
                    if (directory) require(size == 0L) { "zip_directory_payload" }
                    total += size
                    require(total <= MAX_ARCHIVE_BYTES) { "zip_expanded_size_limit" }
                    if (name == "metadata.json") require(size <= SecureZipMetadataLimits.MAX_METADATA_JSON_BYTES) { "zip_metadata_size_limit" }
                    val member = IosZipMember(name, size, compressed, crc, method, flags, offset, directory)
                    // Local file name/method/flags must agree with central data; descriptor ZIPs use central CRC/size.
                    file.source(offset).buffer().use { local ->
                        require(local.readIntLe() == 0x04034b50) { "zip_local_header" }
                        local.skip(2)
                        require((local.readShortLe().toInt() and 65535) == flags && (local.readShortLe().toInt() and 65535) == method) { "zip_local_mismatch" }
                        local.skip(16)
                        val localNameSize = local.readShortLe().toInt() and 65535
                        val localExtraSize = local.readShortLe().toInt() and 65535
                        require(localNameSize == nameSize && offset + 30 + localNameSize + localExtraSize + compressed <= centralOffset) { "zip_local_bounds" }
                        require(local.readUtf8(localNameSize.toLong()) == name) { "zip_local_name_mismatch" }
                    }
                    entries += member
                }
                require(central.exhausted()) { "zip_central_trailing_data" }
            }
            require(entries.any { it.name == "metadata.json" && !it.directory }) { "missing_metadata_json" }
            return entries
        }
    }

    fun metadata(archivePath: String): String {
        val entry = inspect(archivePath).single { it.name == "metadata.json" }
        val bytes = Buffer()
        copyMember(archivePath, entry, {}, { block, count -> bytes.write(block, 0, count) })
        val raw = bytes.readByteArray().decodeToString(throwOnInvalidSequence = true)
        require(validateMetadataJsonBeforeDecode(raw).isEmpty()) { "invalid_metadata_json" }
        return raw
    }

    /** All members validated before any extraction; output is a fresh private directory owned by the result. */
    fun extractToNewDirectory(archivePath: String, storage: DeviceStorage, checkCancellation: () -> Unit = {}): IosPreparedZip {
        val entries = inspect(archivePath)
        require(DiskSpaceGate.canImport(storage.freeBytes(), entries.sumOf { it.size })) { "insufficient_free_space" }
        val parent = root / "exchange" / "staging"
        privateDirectory(parent)
        val directory = parent / NSUUID().UUIDString
        fs.createDirectory(directory, mustCreate = true)
        val paths = linkedMapOf<String, String>()
        try {
            for (entry in entries.filterNot { it.directory }) {
                checkCancellation()
                SecureZipSandbox.assertExtractTargetAllowed(directory.toString(), entry.name, SecureZipWhitelist.MidnightDancerFullBackup)
                val target = directory / entry.name
                privateDirectory(checkNotNull(target.parent))
                fs.openReadWrite(target, mustCreate = true).use { handle ->
                    handle.sink().buffer().use { output -> copyMember(archivePath, entry, checkCancellation) { block, count -> output.write(block, 0, count) } }
                    handle.flush()
                }
                paths[entry.name] = target.toString()
            }
            checkCancellation()
            return IosPreparedZip(directory.toString(), paths) {
                require(directory.parent == parent && fs.canonicalize(directory).toString().startsWith(root.toString() + "/"))
                fs.deleteRecursively(directory)
            }
        } catch (failure: Throwable) {
            fs.deleteRecursively(directory); throw failure
        }
    }

    /** Unencrypted standard ZIP; callers must name it .zip. Existing output survives every pre-publication error. */
    fun writeStoredArchive(outputPath: String, sources: List<IosZipSource>, checkCancellation: () -> Unit = {}) {
        val output = outputPath.toPath()
        require(output.name.endsWith(".zip", ignoreCase = true)) { "plain_zip_requires_zip_extension" }
        require(sources.size in 1..65534 && sources.map { it.archivePath }.distinct().size == sources.size) { "zip_entry_count_or_duplicate" }
        require(sources.any { it.archivePath == "metadata.json" }) { "missing_metadata_json" }
        sources.forEach { validateName(it.archivePath, false) }
        privateDirectory(checkNotNull(output.parent))
        if (fs.exists(output)) confined(output)
        val temporary = checkNotNull(output.parent) / (".zip-" + NSUUID().UUIDString + ".tmp")
        val entries = mutableListOf<IosZipMember>()
        var position = 0L
        try {
            fs.openReadWrite(temporary, mustCreate = true).use { handle ->
                handle.sink().buffer().use { sink ->
                    for (source in sources) {
                        checkCancellation()
                        val path = source.sourcePath.toPath()
                        require(fs.metadata(path).isRegularFile && fs.canonicalize(path) != output) { "zip_invalid_source" }
                        val size = fs.metadata(path).size ?: error("zip_unknown_source_size")
                        require(size in 0..MAX_ARCHIVE_BYTES && position + size <= MAX_ARCHIVE_BYTES) { "zip_size_limit" }
                        if (source.archivePath == "metadata.json") require(size <= SecureZipMetadataLimits.MAX_METADATA_JSON_BYTES) { "zip_metadata_size_limit" }
                        val crc = crcOf(path, checkCancellation)
                        val name = source.archivePath.encodeToByteArray()
                        require(name.size <= 4096) { "zip_name_bounds" }
                        entries += IosZipMember(source.archivePath, size, size, crc, 0, 2048, position, false)
                        sink.writeIntLe(0x04034b50).writeShortLe(20).writeShortLe(2048).writeShortLe(0).writeIntLe(0)
                            .writeIntLe(crc.toInt()).writeIntLe(size.toInt()).writeIntLe(size.toInt()).writeShortLe(name.size).writeShortLe(0).write(name)
                        val second = ZipCrc32(); var copied = 0L
                        fs.source(path).buffer().use { input ->
                            val block = ByteArray(65536)
                            while (true) {
                                checkCancellation(); val count = input.read(block); if (count == -1) break
                                copied += count; require(copied <= size) { "zip_source_changed" }
                                second.update(block, count); sink.write(block, 0, count)
                            }
                        }
                        require(copied == size && second.value == crc) { "zip_source_changed" }
                        position += 30 + name.size + size
                    }
                    val centralOffset = position
                    for (entry in entries) {
                        val name = entry.name.encodeToByteArray()
                        sink.writeIntLe(0x02014b50).writeShortLe(20).writeShortLe(20).writeShortLe(2048).writeShortLe(0).writeIntLe(0)
                            .writeIntLe(entry.crc32.toInt()).writeIntLe(entry.size.toInt()).writeIntLe(entry.size.toInt())
                            .writeShortLe(name.size).writeShortLe(0).writeShortLe(0).writeShortLe(0).writeShortLe(0).writeIntLe(0)
                            .writeIntLe(entry.localOffset.toInt()).write(name)
                        position += 46 + name.size
                    }
                    require(position + 22 <= MAX_ARCHIVE_BYTES) { "zip_size_limit" }
                    sink.writeIntLe(0x06054b50).writeShortLe(0).writeShortLe(0).writeShortLe(entries.size).writeShortLe(entries.size)
                        .writeIntLe((position - centralOffset).toInt()).writeIntLe(centralOffset.toInt()).writeShortLe(0)
                }
                handle.flush()
            }
            checkCancellation()
            metadata(temporary.toString()) // Validate structure and actual metadata CRC before publication.
            fs.atomicMove(temporary, output)
        } finally { fs.delete(temporary, mustExist = false) }
    }

    private fun copyMember(archivePath: String, entry: IosZipMember, check: () -> Unit, consume: (ByteArray, Int) -> Unit) {
        val zip = fs.openZip(archivePath.toPath())
        try {
            zip.source(("/" + entry.name).toPath()).buffer().use { input ->
                val block = ByteArray(65536); val crc = ZipCrc32(); var total = 0L
                while (true) {
                    check(); val count = input.read(block); if (count == -1) break
                    total += count; require(total <= entry.size && total <= MAX_ARCHIVE_BYTES) { "zip_expanded_size_limit" }
                    crc.update(block, count); consume(block, count)
                }
                require(total == entry.size && crc.value == entry.crc32) { "zip_crc_or_size_mismatch" }
            }
        } finally { zip.close() }
    }
    private fun crcOf(path: Path, check: () -> Unit): Long {
        val crc = ZipCrc32(); var read = 0L
        fs.source(path).buffer().use { input ->
            val block = ByteArray(65536)
            while (true) { check(); val n = input.read(block); if (n == -1) break; read += n; require(read <= MAX_ARCHIVE_BYTES); crc.update(block, n) }
        }
        return crc.value
    }
    private fun privateDirectory(path: Path) {
        require(path.isAbsolute && path.toString().split('/').none { it == "." || it == ".." } &&
            (path == root || path.toString().startsWith(root.toString() + "/"))) { "zip_output_outside_private_root" }
        var existing = path
        while (!fs.exists(existing)) existing = checkNotNull(existing.parent)
        confined(existing) // Check before mkdirs, including an existing ancestor symlink.
        fs.createDirectories(path)
        confined(path)
    }
    private fun confined(path: Path) {
        val canonical = fs.canonicalize(path).toString()
        require(canonical == root.toString() || canonical.startsWith(root.toString() + "/")) { "zip_output_outside_private_root" }
    }
    private fun validateName(name: String, directory: Boolean) {
        require(name == name.trim() && '\\' !in name && '\u0000' !in name && !entryPathFailsZipSlipChecks(name)) { "zip_slip" }
        require(name.split('/').filter { it.isNotEmpty() }.none { it == "." || it == ".." } && !name.startsWith('/') && "//" !in name) { "zip_ambiguous_path" }
        if (!directory) require(SecureZipWhitelist.MidnightDancerFullBackup.matchesEntryPath(name)) { "not_whitelisted:$name" }
    }
    companion object {
        const val MAX_ARCHIVE_BYTES = 300L * 1024 * 1024
        private fun u16(bytes: ByteArray, at: Int) = (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)
        private fun u32(bytes: ByteArray, at: Int) = (u16(bytes, at).toLong() or (u16(bytes, at + 2).toLong() shl 16))
    }
}

class IosPreparedZip internal constructor(val directory: String, val resolvedPaths: Map<String, String>, private val cleanup: () -> Unit) {
    private var closed = false
    /** Staged paths expire after close; callers must commit/copy immutable media first. */
    fun close() { if (!closed) { cleanup(); closed = true } }
}

class IosZipMetadataReader(private val archive: IosSecureZipArchive = IosSecureZipArchive()) : SecureZipMetadataReader {
    override fun readMetadataJson(absoluteArchivePath: String): String = archive.metadata(absoluteArchivePath)
}

internal class ZipCrc32 {
    private var crc = -1
    val value: Long get() = (crc.inv().toLong() and 0xffffffffL)
    fun update(bytes: ByteArray, count: Int) { for (i in 0 until count) crc = TABLE[(crc xor bytes[i].toInt()) and 255] xor (crc ushr 8) }
    companion object {
        private val TABLE = IntArray(256) { initial -> var n = initial; repeat(8) { n = if (n and 1 != 0) (n ushr 1) xor 0xedb88320.toInt() else n ushr 1 }; n }
    }
}
