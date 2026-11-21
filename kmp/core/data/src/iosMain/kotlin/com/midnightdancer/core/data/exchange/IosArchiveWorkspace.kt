package com.midnightdancer.core.data.exchange

import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.storage.DiskSpaceGate
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import platform.Foundation.NSUUID

/** Caller retains picked security-scoped access until copy finishes. Only this operation's files are cleaned. */
@OptIn(ExperimentalForeignApi::class)
internal class IosArchiveWorkspace(privateRoot: String, private val storage: DeviceStorage) {
    val fs = FileSystem.SYSTEM
    val root = fs.canonicalize(privateRoot.toPath())
    val directory: Path
    init {
        val parent = root / "exchange" / "operations"
        privateDirectory(parent)
        directory = parent / NSUUID().UUIDString
        fs.createDirectory(directory, mustCreate = true)
    }
    fun privateDirectory(path: Path) {
        require(path.isAbsolute && path.toString().startsWith(root.toString() + "/") && path.toString().split('/').none { it == "." || it == ".." }) { "archive_private_path_required" }
        var ancestor = path
        while (!fs.exists(ancestor)) ancestor = checkNotNull(ancestor.parent)
        val canonical = fs.canonicalize(ancestor).toString()
        require(canonical == root.toString() || canonical.startsWith(root.toString() + "/")) { "archive_private_path_escape" }
        fs.createDirectories(path)
        require(fs.canonicalize(path).toString().startsWith(root.toString() + "/")) { "archive_private_path_escape" }
    }
    fun source(raw: String): Path {
        // Paths are provided by the native owned-media resolver/picker. Never guess alternate files.
        require(raw.startsWith('/') && !raw.contains("://")) { "archive_requires_local_absolute_path" }
        val path = raw.toPath()
        require(fs.metadata(path).isRegularFile && fs.metadata(path).symlinkTarget == null) { "archive_source_unavailable" }
        return fs.canonicalize(path)
    }
    fun copyImmutable(raw: String, name: String, limit: Long, check: () -> Unit): Path {
        require(name.matches(Regex("[a-zA-Z0-9_.-]+")))
        val source = source(raw)
        val before = fs.metadata(source)
        val size = before.size ?: error("archive_unknown_size")
        require(size in 1..limit) { "archive_size_limit" }
        require(DiskSpaceGate.canImport(storage.freeBytes(), size)) { "insufficient_free_space" }
        val target = directory / name
        fs.openReadWrite(target, mustCreate = true).use { handle ->
            handle.sink().buffer().use { output ->
                fs.source(source).buffer().use { input ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
                        check(); val read = input.read(buffer); if (read == -1) break
                        total += read; require(total <= size && total <= limit) { "archive_source_changed" }
                        output.write(buffer, 0, read)
                    }
                    require(total == size) { "archive_source_changed" }
                }
            }
            handle.flush()
        }
        val after = fs.metadata(source)
        require(after.size == before.size && after.lastModifiedAtMillis == before.lastModifiedAtMillis) { "archive_source_changed" }
        return target
    }
    fun text(name: String, raw: String): Path {
        require(name.matches(Regex("[a-zA-Z0-9_.-]+")))
        val path = directory / name
        fs.sink(path, mustCreate = true).buffer().use { it.writeUtf8(raw) }
        return path
    }
    fun close() {
        require(directory.parent == root / "exchange" / "operations")
        val canonical = fs.canonicalize(directory).toString()
        require(canonical.startsWith(root.toString() + "/"))
        fs.deleteRecursively(directory)
    }
}
