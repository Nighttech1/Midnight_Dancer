package com.midnightdancer.core.data.zip

/**
 * Which ZIP member paths may be counted (preflight) and extracted.
 * Parity with Flutter [SecureZipWhitelist].
 */
data class SecureZipWhitelist(
    val allowedExtensionsLowercase: Set<String>,
    val exactFileNamesLowercase: Set<String>,
) {
    /**
     * Normalized archive member path uses forward slashes, no leading `/`.
     * Directories (trailing `/`) never match.
     */
    fun matchesEntryPath(entryPath: String): Boolean {
        val norm = entryPath.replace('\\', '/').trim()
        if (norm.isEmpty() || norm.endsWith('/')) return false
        val name =
            norm
                .split('/')
                .filter { it.isNotEmpty() }
                .lastOrNull()
                ?.lowercase()
                ?: return false
        if (name in exactFileNamesLowercase) return true
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot >= name.length - 1) return false
        val ext = name.substring(dot + 1).lowercase()
        return ext in allowedExtensionsLowercase
    }

    companion object {
        /** Default user ZIP: video/image + metadata.json */
        val DefaultUserSpec: SecureZipWhitelist =
            SecureZipWhitelist(
                allowedExtensionsLowercase = setOf("mp4", "mov", "jpg"),
                exactFileNamesLowercase = setOf("metadata.json"),
            )

        /** Full backup archives from this app. */
        val MidnightDancerFullBackup: SecureZipWhitelist =
            SecureZipWhitelist(
                allowedExtensionsLowercase = setOf("mp4", "mov", "jpg", "mp3", "m4a", "wav"),
                exactFileNamesLowercase = setOf("metadata.json", "manifest.json"),
            )
    }
}
