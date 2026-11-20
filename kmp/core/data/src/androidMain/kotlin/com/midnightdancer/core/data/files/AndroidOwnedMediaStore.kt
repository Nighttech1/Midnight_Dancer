package com.midnightdancer.core.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import com.midnightdancer.core.domain.media.*
import com.midnightdancer.core.domain.storage.DeviceStorage
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Private immutable blobs; no garbage collection and no original-source deletion. */
class AndroidOwnedMediaStore(
    context: Context,
    private val storage: DeviceStorage? = null,
    private val minFreeBytes: Long = 16L * 1024 * 1024,
    private val maxImportBytes: Long = Long.MAX_VALUE,
    private val validator: OwnedMediaValidator = AndroidOwnedMediaValidator(),
) : OwnedMediaStore {
    private val files = context.filesDir.absoluteFile.normalize()
    private val resolver = context.contentResolver
    private val pool = File(files, "MidnightDancer/owned_media/v1")
    private val staging = File(pool, "_staging")
    private val aliasFile = AtomicFile(File(files, "owned_media_aliases_v1.json"))
    private val mutation = locks.computeIfAbsent(files.path) { Mutex() }
    private val initialization = Mutex()
    @Volatile private var initialized = false
    @Volatile private var aliases: Map<String, String> = emptyMap()

    init { require(minFreeBytes >= 0 && maxImportBytes > 0) }

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        initialization.withLock {
            if (!initialized) mutation.withLock {
                aliases = readAliases()
                initialized = true
            }
        }
    }

    override fun resolveReference(reference: String): String {
        check(initialized) { "Owned media resolver must be initialized before use" }
        return aliases[reference] ?: reference
    }

    override fun isDurableOwnedReference(reference: String): Boolean = runCatching {
        val file = fileReference(reference) ?: return@runCatching false
        file.isFile && file.canRead() && durableLocation(file)
    }.getOrDefault(false)

    override suspend fun retain(reference: String, kind: OwnedMediaKind, onProgress: ((OwnedMediaProgress) -> Unit)?): OwnedMedia =
        operation {
            initialize()
            val copied = retainCurrent(reference, kind, onProgress)
            protectAlias(reference, copied.reference)
            onProgress?.invoke(OwnedMediaProgress(copied.bytes, copied.bytes, OwnedMediaPhase.Ready))
            copied
        }

    override suspend fun adopt(reference: String, kind: OwnedMediaKind, onProgress: ((OwnedMediaProgress) -> Unit)?): OwnedMedia =
        operation {
            initialize()
            val prior = aliases[reference]
            if (prior != null) return@operation retainCurrent(prior, kind, onProgress).also {
                onProgress?.invoke(OwnedMediaProgress(it.bytes, it.bytes, OwnedMediaPhase.Ready))
            }
            val copied = retainCurrent(reference, kind, onProgress)
            val winner = protectAlias(reference, copied.reference)
            val result = if (winner == copied.reference) copied else retainCurrent(winner, kind, onProgress)
            onProgress?.invoke(OwnedMediaProgress(result.bytes, result.bytes, OwnedMediaPhase.Ready))
            result
        }

    private suspend fun protectAlias(reference: String, copiedReference: String): String {
        if (isDurableOwnedReference(reference)) return copiedReference
        return mutation.withLock {
            currentCoroutineContext().ensureActive()
            // Per-root immutable first adoption, also for successful new picks protecting old snapshots.
            val latest = readAliases()
            val existing = latest[reference]
            if (existing != null) {
                aliases = latest
                existing
            } else {
                val next = latest + (reference to copiedReference)
                writeAliases(next)
                aliases = next
                copiedReference
            }
        }
    }

    override suspend fun inspect(): OwnedMediaStats = operation {
        initialize()
        mutation.withLock {
            var count = 0
            var bytes = 0L
            pool.listFiles().orEmpty().filter { it.isDirectory && HASH.matches(it.name) }.forEach { directory ->
                directory.listFiles().orEmpty().filter { it.isFile && BLOB.matches(it.name) }.forEach {
                    count++; bytes += it.length()
                }
            }
            OwnedMediaStats(count, bytes, aliases.size, freeBytes())
        }
    }

    private suspend fun retainCurrent(reference: String, kind: OwnedMediaKind, progress: ((OwnedMediaProgress) -> Unit)?): OwnedMedia {
        currentCoroutineContext().ensureActive()
        if (reference.isBlank()) throw OwnedMediaException(OwnedMediaFailure.Unavailable)
        val direct = fileReference(reference)
        if (direct != null && durableLocation(direct)) {
            if (!direct.isFile || !direct.canRead()) throw OwnedMediaException(OwnedMediaFailure.Unavailable)
            val fingerprint = digest(direct)
            if (inside(direct, pool) && direct.parentFile?.name != fingerprint)
                throw OwnedMediaException(OwnedMediaFailure.CorruptOwned)
            validator.validatedExtension(direct, kind)
            currentCoroutineContext().ensureActive()
            return OwnedMedia(direct.canonicalPath, fingerprint, direct.length(), true)
        }
        val source = source(reference)
        source.use {
            if (source.length != null && source.length > maxImportBytes) throw OwnedMediaException(OwnedMediaFailure.NoSpace)
            checkSpace(source.length ?: 1L)
            check(staging.mkdirs() || staging.isDirectory)
            val stage = File(staging, UUID.randomUUID().toString()).apply { check(mkdir()) }
            try {
                val temporary = File(stage, "source.tmp")
                val hash = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                var nextReport = 256L * 1024
                progress?.invoke(OwnedMediaProgress(0, source.length))
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = source.input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        if (copied > maxImportBytes - read) throw OwnedMediaException(OwnedMediaFailure.NoSpace)
                        checkSpace(read.toLong())
                        output.write(buffer, 0, read)
                        hash.update(buffer, 0, read)
                        copied += read
                        if (copied >= nextReport) {
                            progress?.invoke(OwnedMediaProgress(copied, source.length))
                            nextReport = copied + 256 * 1024
                        }
                    }
                    if (copied == 0L) throw OwnedMediaException(OwnedMediaFailure.Unsupported)
                    output.fd.sync()
                }
                if (source.length != null && source.length != copied)
                    throw OwnedMediaException(OwnedMediaFailure.Unavailable)
                if (source.file != null && (source.file.length() != copied || source.file.lastModified() != source.modified))
                    throw OwnedMediaException(OwnedMediaFailure.Unavailable)
                currentCoroutineContext().ensureActive()
                val extension = validator.validatedExtension(temporary, kind)
                if (extension !in EXTENSIONS) throw OwnedMediaException(OwnedMediaFailure.Unsupported)
                val fingerprint = hash.digest().hex()
                progress?.invoke(OwnedMediaProgress(copied, source.length, OwnedMediaPhase.Publishing))
                return mutation.withLock {
                    currentCoroutineContext().ensureActive()
                    val destination = File(pool, fingerprint)
                    if (destination.exists()) {
                        val matches = destination.listFiles().orEmpty()
                        val existing = matches.singleOrNull()?.takeIf { it.isFile && BLOB.matches(it.name) }
                            ?: throw OwnedMediaException(OwnedMediaFailure.CorruptOwned)
                        if (existing.length() != copied || digest(existing) != fingerprint)
                            throw OwnedMediaException(OwnedMediaFailure.CorruptOwned)
                        OwnedMedia(existing.canonicalPath, fingerprint, copied, true)
                    } else {
                        val blob = File(stage, "source.$extension")
                        Files.move(temporary.toPath(), blob.toPath(), StandardCopyOption.ATOMIC_MOVE)
                        currentCoroutineContext().ensureActive()
                        // Atomic directory publication: no empty/partial digest directory becomes visible.
                        Files.move(stage.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
                        OwnedMedia(File(destination, blob.name).canonicalPath, fingerprint, copied, false)
                    }
                }
            } finally {
                // This UUID directory alone is ours; a published/shared digest directory is never removed.
                stage.listFiles().orEmpty().forEach { it.delete() }
                stage.delete()
            }
        }
    }

    private data class Source(val input: InputStream, val length: Long?, val file: File? = null, val modified: Long = 0) : java.io.Closeable {
        override fun close() = input.close()
    }
    private fun source(reference: String): Source {
        val file = fileReference(reference)
        if (file != null) {
            if (!file.isFile || !file.canRead()) throw OwnedMediaException(OwnedMediaFailure.Unavailable)
            return Source(file.inputStream(), file.length(), file, file.lastModified())
        }
        if (!reference.startsWith("content://", true)) throw OwnedMediaException(OwnedMediaFailure.Unsupported)
        val uri = Uri.parse(reference)
        val length = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0 } else null
            }
        }.getOrNull()
        val stream = try { resolver.openInputStream(uri) } catch (e: Exception) {
            throw OwnedMediaException(OwnedMediaFailure.Unavailable, e)
        } ?: throw OwnedMediaException(OwnedMediaFailure.Unavailable)
        return Source(stream, length)
    }
    private fun fileReference(reference: String): File? = when {
        reference.startsWith("file://", true) -> Uri.parse(reference).path?.let(::File)
        else -> File(reference).takeIf { it.isAbsolute }
    }
    private fun inside(file: File, parent: File): Boolean = file.canonicalPath.startsWith(parent.canonicalPath + File.separator)
    /** Only published permanent media roots, never arbitrary filesDir staging/inspection/cache assets. */
    private fun durableLocation(file: File): Boolean {
        if (inside(file, pool)) {
            val relative = file.canonicalPath.removePrefix(pool.canonicalPath + File.separator).replace(File.separatorChar, '/')
            return Regex("[a-f0-9]{64}/source\\.(mp4|mov|mp3|m4a|wav|aac|ogg|flac)").matches(relative)
        }
        return listOf("MidnightDancer/music", "MidnightDancer/videos", "MidnightDancer/exchange/imported_media")
            .any { inside(file, File(files, it)) }
    }
    private fun freeBytes() = storage?.freeBytes() ?: files.usableSpace
    private fun checkSpace(additional: Long) {
        val free = freeBytes()
        if (free < minFreeBytes || additional > free - minFreeBytes) throw OwnedMediaException(OwnedMediaFailure.NoSpace)
    }
    private suspend fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                hash.update(buffer, 0, read)
            }
        }
        return hash.digest().hex()
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    private fun readAliases(): Map<String, String> {
        if (!aliasFile.baseFile.exists() && !File(aliasFile.baseFile.path + ".bak").exists()) return emptyMap()
        try {
            val raw = aliasFile.openRead().use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= MAX_ALIAS_BYTES)
                    out.write(buffer, 0, count)
                }
                out.toString(Charsets.UTF_8.name())
            }
            val document = Json.parseToJsonElement(raw).jsonObject
            require(document["version"]?.jsonPrimitive?.intOrNull == 1)
            val entries = document["aliases"]?.jsonObject ?: error("missing aliases")
            return entries.mapValues { (_, value) ->
                val primitive = value as? JsonPrimitive ?: error("invalid alias")
                require(primitive.isString)
                val target = fileReference(primitive.content) ?: error("invalid alias")
                require(durableLocation(target))
                primitive.content
            }.toMap()
        } catch (e: Exception) { throw OwnedMediaException(OwnedMediaFailure.CorruptOwned, e) }
    }
    private fun writeAliases(map: Map<String, String>) {
        val raw = buildJsonObject {
            put("version", 1)
            put("aliases", JsonObject(map.mapValues { JsonPrimitive(it.value) }))
        }.toString().encodeToByteArray()
        if (raw.size > MAX_ALIAS_BYTES) throw OwnedMediaException(OwnedMediaFailure.IO)
        checkSpace(raw.size.toLong())
        val out = aliasFile.startWrite()
        try {
            out.write(raw)
            aliasFile.finishWrite(out)
        } catch (e: Throwable) {
            aliasFile.failWrite(out)
            throw e
        }
    }
    private suspend fun <T> operation(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: OwnedMediaException) { throw e }
        catch (e: Exception) {
            val reason = if (runCatching { freeBytes() <= minFreeBytes }.getOrDefault(false) ||
                e.message.orEmpty().contains("ENOSPC", true)) OwnedMediaFailure.NoSpace else OwnedMediaFailure.IO
            throw OwnedMediaException(reason, e)
        }
    }
    companion object {
        private val locks = ConcurrentHashMap<String, Mutex>()
        private val HASH = Regex("[a-f0-9]{64}")
        private val BLOB = Regex("source\\.(mp4|mov|mp3|m4a|wav|aac|ogg|flac)")
        private val EXTENSIONS = setOf("mp4", "mov", "mp3", "m4a", "wav", "aac", "ogg", "flac")
        private const val MAX_ALIAS_BYTES = 2 * 1024 * 1024
    }
}
