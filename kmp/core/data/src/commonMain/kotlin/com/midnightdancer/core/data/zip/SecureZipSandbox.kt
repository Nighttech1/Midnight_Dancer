package com.midnightdancer.core.data.zip

/**
 * Single extract gate for secure ZIP members.
 *
 * **Contract:** any future byte extract / write of an archive member MUST call
 * [assertExtractTargetAllowed] (or [isExtractTargetAllowed]) before creating the
 * target file. Full sandbox extract + wipe remains a later stage; this gate is
 * the mandatory pre-write check (zip-slip + whitelist + sandbox containment).
 */
object SecureZipSandbox {
    /**
     * Fail-closed: throws [IllegalArgumentException] when [entryPath] must not be written
     * under [sandboxRoot].
     */
    fun assertExtractTargetAllowed(
        sandboxRoot: String,
        entryPath: String,
        whitelist: SecureZipWhitelist = SecureZipWhitelist.DefaultUserSpec,
    ) {
        val norm = entryPath.replace('\\', '/').trim()
        require(norm.isNotEmpty()) { "empty_entry_path" }
        require(!entryPathFailsZipSlipChecks(norm)) { "zip_slip:$norm" }
        require(whitelist.matchesEntryPath(norm)) { "not_whitelisted:$norm" }
        require(isInsideSandbox(sandboxRoot, norm)) { "outside_sandbox:$norm" }
    }

    /** Same checks as [assertExtractTargetAllowed] without throwing. */
    fun isExtractTargetAllowed(
        sandboxRoot: String,
        entryPath: String,
        whitelist: SecureZipWhitelist = SecureZipWhitelist.DefaultUserSpec,
    ): Boolean =
        runCatching {
            assertExtractTargetAllowed(sandboxRoot, entryPath, whitelist)
        }.isSuccess
}