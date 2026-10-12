package com.midnightdancer.core.data.files

/** Join absolute / relative path segments with `/` (POSIX + Android filesDir). */
internal fun joinPath(vararg parts: String): String {
    require(parts.isNotEmpty()) { "joinPath requires at least one segment" }
    val cleaned =
        parts
            .asSequence()
            .map { it.replace('\\', '/').trim('/') }
            .filter { it.isNotEmpty() }
            .toList()
    require(cleaned.isNotEmpty()) { "joinPath segments are empty" }
    val first = parts.first().replace('\\', '/')
    val absolute = first.startsWith('/')
    val joined = cleaned.joinToString("/")
    return if (absolute && !joined.startsWith('/')) "/$joined" else joined
}
