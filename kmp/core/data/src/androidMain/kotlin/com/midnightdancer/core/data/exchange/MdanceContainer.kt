package com.midnightdancer.core.data.exchange

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * `.mdance` = magic + version + IV + AES-GCM(stream of inner zip).
 * App-bound key — media not casually extractable as open zip outside the app.
 * Streaming cipher I/O avoids holding multi-GB archives fully in RAM (C-108 / anti-OOM).
 */
object MdanceContainer {
    private val MAGIC = "MDANCE1".encodeToByteArray()
    private const val VERSION: Byte = 1
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN = 12
    private const val BUFFER = 64 * 1024

    private val PEPPER_A = byteArrayOf(0x4D, 0x44, 0x2D, 0x4E, 0x31, 0x67, 0x68, 0x74)
    private val PEPPER_B = byteArrayOf(0x44, 0x61, 0x6E, 0x63, 0x65, 0x72, 0x23, 0x39)

    fun isMdance(file: File): Boolean {
        if (!file.exists() || file.length() < MAGIC.size + 1L + IV_LEN + 16L) return false
        FileInputStream(file).use { input ->
            val magic = ByteArray(MAGIC.size)
            if (input.read(magic) != MAGIC.size) return false
            return magic.contentEquals(MAGIC)
        }
    }

    fun wrapZipFileToMdance(
        zipFile: File,
        outFile: File,
        packageName: String,
    ) {
        val key = deriveKey(packageName)
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val tmp = File(outFile.parentFile, "${outFile.name}.tmp")
        if (tmp.exists()) tmp.delete()
        FileOutputStream(tmp).use { fos ->
            fos.write(MAGIC)
            fos.write(byteArrayOf(VERSION))
            fos.write(iv)
            CipherOutputStream(BufferedOutputStream(fos), cipher).use { cos ->
                BufferedInputStream(FileInputStream(zipFile)).use { input ->
                    input.copyTo(cos, BUFFER)
                }
            }
        }
        if (outFile.exists()) outFile.delete()
        if (!tmp.renameTo(outFile)) {
            tmp.copyTo(outFile, overwrite = true)
            tmp.delete()
        }
    }

    fun unwrapMdanceToZipFile(
        mdanceFile: File,
        zipOut: File,
        packageName: String,
    ) {
        val key = deriveKey(packageName)
        FileInputStream(mdanceFile).use { fis ->
            val magic = ByteArray(MAGIC.size)
            check(fis.read(magic) == MAGIC.size && magic.contentEquals(MAGIC)) {
                "not_mdance_magic"
            }
            val version = fis.read()
            check(version == VERSION.toInt()) { "unsupported_mdance_version:$version" }
            val iv = ByteArray(IV_LEN)
            check(fis.read(iv) == IV_LEN) { "mdance_truncated_iv" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            if (zipOut.exists()) zipOut.delete()
            CipherInputStream(BufferedInputStream(fis), cipher).use { cis ->
                FileOutputStream(zipOut).use { fos ->
                    cis.copyTo(fos, BUFFER)
                }
            }
        }
        check(zipOut.isFile && zipOut.length() > 0L) { "mdance_decrypt_empty" }
    }

    private fun deriveKey(packageName: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(packageName.encodeToByteArray())
        md.update(PEPPER_A)
        md.update(PEPPER_B)
        return md.digest()
    }
}
