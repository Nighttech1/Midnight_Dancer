package com.midnightdancer.core.domain.usecase

/**
 * Preflight / whitelist validation for secure ZIP archives.
 * Contract only — implementation in C-15 (`:core:data` zip layer).
 */
interface ValidateSecureZip {
    suspend fun validate(archivePath: String): SecureZipValidationResult
}

sealed class SecureZipValidationResult {
    data object Ok : SecureZipValidationResult()

    data class Rejected(
        val reasons: List<String>,
    ) : SecureZipValidationResult()
}
