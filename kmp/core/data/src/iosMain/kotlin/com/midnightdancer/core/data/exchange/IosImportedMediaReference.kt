package com.midnightdancer.core.data.exchange

import okio.FileSystem
import okio.Path.Companion.toPath

/** Durable import identities contain no installation-specific sandbox path. Never fall back to a source URI. */
object IosImportedMediaReference {
    const val PREFIX = "md-import-v1/"
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val filename = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,199}")

    @Throws(Exception::class)
    fun create(importId: String, name: String): String {
        require(uuid.matches(importId) && filename.matches(name) && name != "." && name != "..") { "invalid_import_reference" }
        return PREFIX + importId + "/" + name
    }
    @Throws(Exception::class)
    fun directory(importId: String): String {
        require(uuid.matches(importId)) { "invalid_import_reference" }
        return PREFIX + importId
    }
    @Throws(Exception::class)
    fun resolve(privateRoot: String, reference: String): String {
        val parts = reference.split('/')
        require(parts.size == 3 && parts[0] == "md-import-v1" && create(parts[1], parts[2]) == reference) { "invalid_import_reference" }
        return resolveDirectory(privateRoot, directory(parts[1])) + "/" + parts[2]
    }
    @Throws(Exception::class)
    fun resolveDirectory(privateRoot: String, reference: String): String {
        val parts = reference.split('/')
        require(parts.size == 2 && parts[0] == "md-import-v1" && uuid.matches(parts[1])) { "invalid_import_reference" }
        val root = FileSystem.SYSTEM.canonicalize(privateRoot.toPath())
        return (root / "exchange" / "imports" / parts[1]).toString()
    }
}
