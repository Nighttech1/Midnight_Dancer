package com.midnightdancer.core.data.files

import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSUUID

/** Private Documents file, checked before replacement; failures never become an empty document. */
@OptIn(ExperimentalForeignApi::class)
class IosDurableTextFile(
    relativePath: String,
    documentsDirectory: String = platformDocumentsDirectoryPath(),
) {
    private val fs = FileSystem.SYSTEM
    private val root = fs.canonicalize(documentsDirectory.toPath())
    private val path: Path
    val absolutePath: String get() = path.toString()

    init {
        require(relativePath.isNotBlank() && !relativePath.startsWith('/') && '\\' !in relativePath && '\u0000' !in relativePath)
        require(relativePath.split('/').none { it == ".." || it == "." || it.isBlank() })
        path = root / relativePath
    }

    fun readOrNull(): String? = locked {
        if (!fs.exists(path)) return@locked null
        checkConfined(path)
        require(fs.metadata(path).isRegularFile) { "ios_library_not_a_file" }
        // A private metadata limit fails closed, without truncation or replacement.
        val size = fs.metadata(path).size ?: error("ios_library_unknown_size")
        require(size <= MAX_METADATA_BYTES) { "ios_library_metadata_too_large" }
        fs.source(path).buffer().use { input ->
            val buffer = okio.Buffer()
            while (input.read(buffer, 64L * 1024) != -1L) {
                require(buffer.size <= MAX_METADATA_BYTES) { "ios_library_metadata_too_large" }
            }
            buffer.readByteArray().decodeToString(throwOnInvalidSequence = true)
        }
    }

    fun write(raw: String) = locked {
        val bytes = raw.encodeToByteArray()
        require(bytes.size.toLong() <= MAX_METADATA_BYTES) { "ios_library_metadata_too_large" }
        atomicWrite { sink -> sink.write(bytes) }
    }

    /** New archive identity only. Never deletes the source or replaces an existing owned archive. */
    fun retainCopy(sourcePath: String): String = locked {
        val source = sourcePath.removePrefix("file://").toPath()
        require(source.isAbsolute && fs.exists(source) && fs.metadata(source).isRegularFile) { "ios_archive_unavailable" }
        if (fs.exists(path)) {
            require(fs.canonicalize(source) == fs.canonicalize(path)) { "ios_archive_identity_exists" }
            return@locked absolutePath
        }
        atomicWrite { sink -> fs.source(source).buffer().use { input -> sink.writeAll(input) } }
        absolutePath
    }

    private fun atomicWrite(write: (okio.BufferedSink) -> Unit) {
        val parent = checkNotNull(path.parent)
        fs.createDirectories(parent)
        checkConfined(parent)
        if (fs.exists(path)) checkConfined(path)
        val temporary = parent / (".md-library-" + NSUUID().UUIDString + ".tmp")
        try {
            fs.openReadWrite(temporary, mustCreate = true).use { handle ->
                handle.sink().buffer().use { sink -> write(sink) }
                handle.flush()
            }
            fs.atomicMove(temporary, path)
        } finally {
            fs.delete(temporary, mustExist = false)
        }
    }

    private fun checkConfined(candidate: Path) {
        val resolved = fs.canonicalize(candidate).toString()
        require(resolved == root.toString() || resolved.startsWith(root.toString() + "/")) { "ios_library_path_escape" }
    }

    private fun <T> locked(block: () -> T): T {
        fileLock.lock()
        try { return block() } finally { fileLock.unlock() }
    }

    private companion object {
        val fileLock = NSRecursiveLock()
        const val MAX_METADATA_BYTES = 64L * 1024 * 1024
    }
}
