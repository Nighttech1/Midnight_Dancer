package com.midnightdancer.core.data.exchange

/**
 * Required native authenticated-container dependency, deliberately without a default implementation.
 * ZIP support is not encryption: callers must never rename a plain ZIP to .dance/.mdance.
 *
 * Implementations must preserve Android v1: MDANCE1 + byte(1) + nonce(12) + AES-256-GCM
 * ciphertext + tag(16), no AAD; key SHA256(UTF8(bundleId) + "MD-N1ght" + "Dancer#9").
 * Work with bounded buffers up to 300 MiB, check cancellation during I/O, authenticate before
 * publishing plaintext, atomically publish to a private destination, preserve existing output
 * on failure and delete only implementation-owned temporary files. No key/payload diagnostics.
 * CryptoKit's one-shot Data API alone does not establish that bounded-memory contract.
 */
interface IosArchiveContainerCodec {
    fun decryptToPrivateZip(containerPath: String, destinationPath: String, bundleId: String, checkCancellation: () -> Unit)
    fun encryptPrivateZip(zipPath: String, destinationPath: String, bundleId: String, checkCancellation: () -> Unit)
}
