package com.midnightdancer.core.data.zip

import com.midnightdancer.core.domain.usecase.SecureZipValidationResult
import com.midnightdancer.core.domain.usecase.ValidateSecureZip

/**
 * Validate archive member paths: entry-count cap + zip-slip reject + whitelist.
 * Does not extract bytes (C-16 audits extract surface; use [SecureZipSandbox] before write).
 */
class DefaultValidateSecureZip(
    private val inspector: ZipArchiveInspector,
    private val whitelist: SecureZipWhitelist = SecureZipWhitelist.DefaultUserSpec,
    private val limits: SecureZipLimits = SecureZipLimits.FlutterParity,
    private val preflight: SecureZipPreflight = DefaultSecureZipPreflight,
) : ValidateSecureZip {
    override suspend fun validate(archivePath: String): SecureZipValidationResult {
        val listed = inspector.listEntryPaths(archivePath)
        val entries =
            listed.getOrElse { e ->
                return SecureZipValidationResult.Rejected(
                    listOf("cannot_list_archive: ${e.message ?: e::class.simpleName}"),
                )
            }
        return validateEntryPaths(entries, whitelist, limits, preflight)
    }

    companion object {
        fun validateEntryPaths(
            entries: List<String>,
            whitelist: SecureZipWhitelist = SecureZipWhitelist.DefaultUserSpec,
            limits: SecureZipLimits = SecureZipLimits.FlutterParity,
            preflight: SecureZipPreflight = DefaultSecureZipPreflight,
        ): SecureZipValidationResult {
            when (val pf = preflight.checkListedEntryCount(entries.size, limits)) {
                is SecureZipPreflightCheck.Rejected ->
                    return SecureZipValidationResult.Rejected(pf.reasons)
                SecureZipPreflightCheck.Ok -> Unit
            }
            val reasons = mutableListOf<String>()
            if (entries.isEmpty()) {
                reasons += "empty_archive"
            }
            var hasAllowedPayload = false
            for (raw in entries) {
                val norm = raw.replace('\\', '/').trim()
                if (norm.endsWith('/')) continue // directory markers
                if (entryPathFailsZipSlipChecks(norm)) {
                    reasons += "zip_slip:$norm"
                    continue
                }
                if (!whitelist.matchesEntryPath(norm)) {
                    reasons += "not_whitelisted:$norm"
                    continue
                }
                hasAllowedPayload = true
            }
            if (!hasAllowedPayload && reasons.none { it.startsWith("zip_slip") }) {
                reasons += "no_whitelisted_payload"
            }
            return if (reasons.isEmpty()) {
                SecureZipValidationResult.Ok
            } else {
                SecureZipValidationResult.Rejected(reasons.distinct())
            }
        }
    }
}