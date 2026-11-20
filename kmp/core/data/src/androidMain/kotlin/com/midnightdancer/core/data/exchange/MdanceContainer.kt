package com.midnightdancer.core.data.exchange

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter

/**
 * Unchanged v1: MDANCE1 | version=1 | IV(12) | AES-256-GCM ciphertext | tag(16).
 * Legacy key derivation and absence of AAD are part of this wire contract.
 * Android Conscrypt AEAD buffers the input until doFinal even in CipherOutputStream.
 * BC lightweight GCM processes bounded blocks without registering a JCA provider.
 * Decrypted bytes are private until the final authentication tag is verified.
 */
object MdanceContainer {
    private val MAGIC = "MDANCE1".encodeToByteArray()
    private const val VERSION: Byte = 1
    private const val GCM_TAG_BITS = 128
    private const val TAG_BYTES = 16
    private const val IV_LEN = 12
    private const val BUFFER = 64 * 1024
    private const val PROGRESS_BYTES = 8L * 1024 * 1024
    private const val MAX_PLAIN_BYTES = 300L * 1024 * 1024
    private val PEPPER_A = byteArrayOf(0x4D, 0x44, 0x2D, 0x4E, 0x31, 0x67, 0x68, 0x74)
    private val PEPPER_B = byteArrayOf(0x44, 0x61, 0x6E, 0x63, 0x65, 0x72, 0x23, 0x39)

    fun isMdance(file: File): Boolean {
        if (!file.isFile || file.length() < MAGIC.size + 1L + IV_LEN + TAG_BYTES) return false
        return FileInputStream(file).use { input ->
            val magic = ByteArray(MAGIC.size)
            runCatching { readFully(input, magic) }.isSuccess && magic.contentEquals(MAGIC)
        }
    }

    /** Output belongs in app-private storage; diagnostics contain only phases/counts/classes. */
    fun wrapZipFileToMdance(zipFile: File, outFile: File, packageName: String, diagnostic: ((String) -> Unit)? = null) {
        require(zipFile.isFile) { "mdance_source_missing" }
        val sourceBytes = zipFile.length()
        require(sourceBytes in 1..MAX_PLAIN_BYTES) { "mdance_source_size_limit" }
        require(zipFile.canonicalFile != outFile.canonicalFile) { "mdance_source_equals_output" }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val temporary = privateTemporaryOutput(outFile)
        try {
            emit(diagnostic, "mdance-wrap-start v=1 engine=BC-lightweight bytes=$sourceBytes defaultJcaProvider=${defaultProviderName()}")
            FileOutputStream(temporary).use { fileOutput ->
                BufferedOutputStream(fileOutput, BUFFER).use { output ->
                    output.write(MAGIC)
                    output.write(VERSION.toInt())
                    output.write(iv)
                    BufferedInputStream(FileInputStream(zipFile), BUFFER).use { input ->
                        transform(input, output, true, deriveKey(packageName), iv, sourceBytes, diagnostic)
                    }
                    output.flush()
                    fileOutput.fd.sync()
                }
            }
            publishVerified(temporary, outFile)
            emit(diagnostic, "mdance-wrap-complete inputBytes=$sourceBytes outputBytes=${outFile.length()}")
        } catch (failure: Throwable) {
            emit(diagnostic, "mdance-wrap-failed bytes=$sourceBytes type=${failure.javaClass.simpleName}")
            throw failure
        } finally {
            temporary.delete()
        }
    }

    /** zipOut stays unchanged on authentication, format or I/O failure before publication. */
    fun unwrapMdanceToZipFile(mdanceFile: File, zipOut: File, packageName: String, diagnostic: ((String) -> Unit)? = null, checkCancellation: (() -> Unit)? = null) {
        require(mdanceFile.isFile) { "mdance_source_missing" }
        val sourceBytes = mdanceFile.length()
        val headerBytes = MAGIC.size + 1L + IV_LEN
        require(sourceBytes in (headerBytes + TAG_BYTES + 1)..(headerBytes + TAG_BYTES + MAX_PLAIN_BYTES)) { "mdance_source_size_limit" }
        require(mdanceFile.canonicalFile != zipOut.canonicalFile) { "mdance_source_equals_output" }
        val temporary = privateTemporaryOutput(zipOut)
        try {
            emit(diagnostic, "mdance-unwrap-start v=1 engine=BC-lightweight bytes=$sourceBytes")
            BufferedInputStream(FileInputStream(mdanceFile), BUFFER).use { input ->
                val magic = ByteArray(MAGIC.size)
                readFully(input, magic)
                require(magic.contentEquals(MAGIC)) { "not_mdance_magic" }
                val version = input.read()
                require(version == VERSION.toInt()) { "unsupported_mdance_version:$version" }
                val iv = ByteArray(IV_LEN)
                readFully(input, iv)
                FileOutputStream(temporary).use { fileOutput ->
                    BufferedOutputStream(fileOutput, BUFFER).use { output ->
                        transform(input, output, false, deriveKey(packageName), iv, sourceBytes - headerBytes, diagnostic, checkCancellation)
                        output.flush()
                        fileOutput.fd.sync()
                    }
                }
            }
            // Only transform.doFinal authenticates the complete ciphertext and tag.
            // No importer can read temporary plaintext through the public zipOut path.
            checkCancellation?.invoke()
            publishVerified(temporary, zipOut)
            emit(diagnostic, "mdance-unwrap-complete inputBytes=$sourceBytes outputBytes=${zipOut.length()}")
        } catch (failure: Throwable) {
            emit(diagnostic, "mdance-unwrap-failed bytes=$sourceBytes type=${failure.javaClass.simpleName}")
            throw failure
        } finally {
            temporary.delete()
        }
    }

    private fun transform(input: InputStream, output: OutputStream, encrypting: Boolean, key: ByteArray, iv: ByteArray, expectedInputBytes: Long, diagnostic: ((String) -> Unit)?, checkCancellation: (() -> Unit)? = null) {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(encrypting, AEADParameters(KeyParameter(key), GCM_TAG_BITS, iv))
        val inputBuffer = ByteArray(BUFFER)
        val outputBuffer = ByteArray(BUFFER + 32)
        val inputLimit = MAX_PLAIN_BYTES + if (encrypting) 0 else TAG_BYTES
        val outputLimit = MAX_PLAIN_BYTES + if (encrypting) TAG_BYTES else 0
        var readBytes = 0L
        var writtenBytes = 0L
        var nextProgress = PROGRESS_BYTES
        while (true) {
            checkCancellation?.invoke()
            val count = input.read(inputBuffer)
            if (count < 0) break
            if (count == 0) continue
            readBytes += count
            require(readBytes <= inputLimit) { "mdance_input_size_limit" }
            check(cipher.getUpdateOutputSize(count) <= outputBuffer.size) { "mdance_unbounded_cipher_update" }
            val produced = cipher.processBytes(inputBuffer, 0, count, outputBuffer, 0)
            output.write(outputBuffer, 0, produced)
            writtenBytes += produced
            require(writtenBytes <= outputLimit) { "mdance_output_size_limit" }
            if (readBytes >= nextProgress) {
                emit(diagnostic, "mdance-${if (encrypting) "wrap" else "unwrap"}-progress inputBytes=$readBytes outputBytes=$writtenBytes")
                nextProgress += PROGRESS_BYTES
            }
        }
        require(readBytes == expectedInputBytes) { "mdance_source_changed_during_read" }
        check(cipher.getOutputSize(0) <= outputBuffer.size) { "mdance_unbounded_cipher_final" }
        val finalBytes = cipher.doFinal(outputBuffer, 0)
        writtenBytes += finalBytes
        require(writtenBytes in 1..outputLimit) { "mdance_output_size_limit" }
        require(writtenBytes == readBytes + if (encrypting) TAG_BYTES else -TAG_BYTES) { "mdance_invalid_cipher_length" }
        output.write(outputBuffer, 0, finalBytes)
    }

    private fun readFully(input: InputStream, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            require(count > 0) { "mdance_truncated_header" }
            offset += count
        }
    }

    private fun privateTemporaryOutput(output: File): File {
        val parent = output.absoluteFile.parentFile ?: error("mdance_output_parent_missing")
        check(parent.mkdirs() || parent.isDirectory) { "mdance_output_directory_unavailable" }
        val temporary = File.createTempFile(".${output.name}.", ".tmp", parent)
        try {
            temporary.setReadable(false, false)
            temporary.setWritable(false, false)
            check(temporary.setReadable(true, true) && temporary.setWritable(true, true)) { "mdance_private_temp_permissions" }
            return temporary
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    private fun publishVerified(temporary: File, output: File) {
        // Same-directory atomic rename; never delete/truncate the previous good output first.
        // Unsupported filesystems fail closed rather than partially overwrite an existing file.
        Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun deriveKey(packageName: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(packageName.encodeToByteArray())
        digest.update(PEPPER_A)
        digest.update(PEPPER_B)
        return digest.digest()
    }

    private fun defaultProviderName(): String = runCatching { Cipher.getInstance("AES/GCM/NoPadding").provider.name }.getOrDefault("unavailable")

    private fun emit(diagnostic: ((String) -> Unit)?, message: String) {
        if (diagnostic != null) runCatching { diagnostic(message) }
    }
}
