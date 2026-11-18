package com.midnightdancer.core.data.exchange

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.InvalidCipherTextException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Independent JCA vectors enforce the existing v1 wire format, not just BC self-roundtrips. */
class MdanceContainerTest {
    private lateinit var directory: File
    private val packageName = "com.midnightdancer.app"

    @Before fun setUp() { directory = Files.createTempDirectory("mdance-test-").toFile() }
    @After fun cleanup() {
        check(directory.canonicalFile.parentFile == File(System.getProperty("java.io.tmpdir")).canonicalFile)
        check(directory.name.startsWith("mdance-test-"))
        directory.deleteRecursively()
    }

    @Test fun legacy_jca_v1_archive_decrypts_with_bounded_engine() {
        val clear = ByteArray(8193) { (it * 31).toByte() }
        val source = File(directory, "legacy.dance").apply { writeBytes(legacyEncrypt(clear)) }
        val output = File(directory, "verified.zip")
        assertTrue(MdanceContainer.isMdance(source))
        MdanceContainer.unwrapMdanceToZipFile(source, output, packageName)
        assertArrayEquals(clear, output.readBytes())
        assertNoTemporaryFiles()
    }

    @Test fun new_archive_is_v1_and_decrypts_with_independent_jca_reader() {
        val clear = ByteArray(4097) { (it * 17).toByte() }
        val source = File(directory, "source.zip").apply { writeBytes(clear) }
        val output = File(directory, "new.dance")
        MdanceContainer.wrapZipFileToMdance(source, output, packageName)
        val bytes = output.readBytes()
        assertArrayEquals("MDANCE1".encodeToByteArray(), bytes.copyOfRange(0, 7))
        assertEquals(1, bytes[7].toInt())
        assertEquals(clear.size + 36, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(legacyKey(packageName), "AES"), GCMParameterSpec(128, bytes.copyOfRange(8, 20)))
        assertArrayEquals(clear, cipher.doFinal(bytes.copyOfRange(20, bytes.size)))
        assertNoTemporaryFiles()
    }

    @Test fun corruption_truncation_appending_and_wrong_package_never_replace_previous_verified_output() {
        val clear = ByteArray(513) { it.toByte() }
        val sealed = legacyEncrypt(clear)
        val cases = listOf(
            sealed.copyOf().apply { this[20] = (this[20].toInt() xor 1).toByte() },
            sealed.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() },
            sealed.copyOf().apply { this[8] = (this[8].toInt() xor 1).toByte() },
            sealed.copyOf(sealed.size - 1),
            sealed.copyOf(10),
            sealed + byteArrayOf(0),
            sealed.copyOf().apply { this[0] = 0 },
            sealed.copyOf().apply { this[7] = 2 },
        )
        val previous = byteArrayOf(42, 43, 44)
        val output = File(directory, "verified.zip").apply { writeBytes(previous) }
        cases.forEachIndexed { index, bytes ->
            val bad = File(directory, "bad-$index.dance").apply { writeBytes(bytes) }
            assertTrue("Case $index accepted", runCatching { MdanceContainer.unwrapMdanceToZipFile(bad, output, packageName) }.isFailure)
            assertArrayEquals(previous, output.readBytes())
            assertNoTemporaryFiles()
        }
        val good = File(directory, "good.dance").apply { writeBytes(sealed) }
        val wrongKey = runCatching { MdanceContainer.unwrapMdanceToZipFile(good, output, "another.package") }.exceptionOrNull()
        assertTrue(wrongKey.toString(), wrongKey is InvalidCipherTextException)
        assertArrayEquals(previous, output.readBytes())
        assertNoTemporaryFiles()
    }

    @Test fun decrypted_plaintext_is_not_published_before_final_tag_verification() {
        val source = File(directory, "large-source.zip")
        source.outputStream().use { output ->
            val block = ByteArray(64 * 1024) { (it * 11).toByte() }
            repeat(9 * 16) { output.write(block) }
        }
        val sealed = File(directory, "bad-tag.dance")
        MdanceContainer.wrapZipFileToMdance(source, sealed, packageName)
        RandomAccessFile(sealed, "rw").use { file ->
            file.seek(file.length() - 1)
            val byte = file.read()
            file.seek(file.length() - 1)
            file.write(byte xor 1)
        }
        val previous = byteArrayOf(7, 8)
        val verified = File(directory, "verified.zip").apply { writeBytes(previous) }
        var progressObserved = false
        var publishedEarly = false
        val failure = runCatching {
            MdanceContainer.unwrapMdanceToZipFile(sealed, verified, packageName, diagnostic = { message ->
                if (message.startsWith("mdance-unwrap-progress")) {
                    progressObserved = true
                    if (verified.length() != previous.size.toLong() || !verified.readBytes().contentEquals(previous)) publishedEarly = true
                }
            })
        }.exceptionOrNull()
        assertTrue(failure.toString(), failure is InvalidCipherTextException)
        assertTrue(progressObserved)
        assertFalse(publishedEarly)
        assertArrayEquals(previous, verified.readBytes())
        assertNoTemporaryFiles()
    }

    @Test fun failed_wrap_preserves_previous_ciphertext() {
        val previous = byteArrayOf(10, 11, 12)
        val output = File(directory, "previous.dance").apply { writeBytes(previous) }
        assertTrue(runCatching { MdanceContainer.wrapZipFileToMdance(File(directory, "missing.zip"), output, packageName) }.isFailure)
        assertArrayEquals(previous, output.readBytes())
        assertNoTemporaryFiles()
    }

    private fun assertNoTemporaryFiles() { assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") }) }

    private fun legacyKey(applicationId: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(applicationId.encodeToByteArray())
        digest.update(byteArrayOf(0x4D, 0x44, 0x2D, 0x4E, 0x31, 0x67, 0x68, 0x74))
        digest.update(byteArrayOf(0x44, 0x61, 0x6E, 0x63, 0x65, 0x72, 0x23, 0x39))
        return digest.digest()
    }

    private fun legacyEncrypt(clear: ByteArray): ByteArray {
        val iv = ByteArray(12) { (it + 1).toByte() } // deterministic fixture only, never production encryption
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(legacyKey(packageName), "AES"), GCMParameterSpec(128, iv))
        return ByteArrayOutputStream().use { out ->
            out.write("MDANCE1".encodeToByteArray())
            out.write(1)
            out.write(iv)
            out.write(cipher.doFinal(clear))
            out.toByteArray()
        }
    }
}
