package com.midnightdancer.core.data.zip

/**
 * Rejects `..`, absolute paths, and Windows drive paths inside ZIP entry names.
 * Parity with Flutter [entryPathFailsZipSlipChecks] — no exploit PoC, defensive only.
 *
 * @return true if the path must be **rejected**
 */
fun entryPathFailsZipSlipChecks(entryPath: String): Boolean {
    val norm = entryPath.replace('\\', '/').trim()
    if (norm.isEmpty()) return true
    if (norm.startsWith('/')) return true
    val lower = norm.lowercase()
    if (lower.startsWith("//")) return true
    if (':' in lower) return true // Windows drive / UNC-ish
    val segments = norm.split('/').filter { it.isNotEmpty() }
    return segments.any { it == ".." || it == "." }
}

/** True when [relativeZipPath] would stay under [sandboxAbsolute] after join+normalize. */
fun isInsideSandbox(
    sandboxAbsolute: String,
    relativeZipPath: String,
): Boolean {
    if (entryPathFailsZipSlipChecks(relativeZipPath)) return false
    val baseSegs = pathSegments(sandboxAbsolute)
    if (baseSegs.isEmpty()) return false
    val relSegs =
        relativeZipPath
            .replace('\\', '/')
            .trimStart('/')
            .split('/')
            .filter { it.isNotEmpty() && it != "." }
    if (relSegs.isEmpty()) return false
    if (relSegs.any { it == ".." }) return false
    val joined = baseSegs + relSegs
    return joined.size > baseSegs.size && joined.take(baseSegs.size) == baseSegs
}

private fun pathSegments(absoluteOrRelative: String): List<String> =
    absoluteOrRelative
        .replace('\\', '/')
        .split('/')
        .filter { it.isNotEmpty() && it != "." }
