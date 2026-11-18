package com.midnightdancer.app.exchange

import android.os.StatFs
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.midnightdancer.core.data.exchange.MdanceContainer
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic files only: no library, preferences, databases or user's media are read or written. */
@RunWith(AndroidJUnit4::class)
class MdanceContainerDeviceTest {
    private lateinit var cache: File
    private lateinit var directory: File
    private val packageName = "com.midnightdancer.app"

    @Before fun setUp() {
        cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.canonicalFile
        directory = File(cache, "mdance-container-device-${UUID.randomUUID()}").apply { check(mkdirs()) }
    }

    @After fun cleanup() {
        check(directory.canonicalFile.parentFile == cache)
        check(directory.name.startsWith("mdance-container-device-"))
        directory.deleteRecursively()
    }

    @Test fun large_v1_roundtrip_is_incremental_and_heap_growth_is_bounded_on_device() {
        val plainBytes = 184L * 1024 * 1024
        assertTrue("Insufficient isolated-cache space for the large-file guard",
            StatFs(directory.path).availableBytes > plainBytes * 3 + 32L * 1024 * 1024)
        val source = File(directory, "synthetic-source.zip")
        source.outputStream().use { output ->
            val block = ByteArray(64 * 1024) { (it * 23 + 5).toByte() }
            repeat(184 * 16) { output.write(block) }
        }
        val expected = digest(source)
        val sealed = File(directory, "synthetic.dance")
        val verified = File(directory, "verified.zip")
        Runtime.getRuntime().gc()
        val beforeHeap = usedHeap()
        var peakHeap = beforeHeap
        var wrapProgress = 0
        var unwrapProgress = 0
        var nonIncremental = false
        val started = System.nanoTime()
        val observe: (String) -> Unit = { message ->
            Log.i("MdContainerQA", message)
            peakHeap = maxOf(peakHeap, usedHeap())
            if (message.contains("-progress")) {
                val input = Regex("inputBytes=(\\d+)").find(message)!!.groupValues[1].toLong()
                val output = Regex("outputBytes=(\\d+)").find(message)!!.groupValues[1].toLong()
                if (input - output > 32L) nonIncremental = true
                if (message.startsWith("mdance-wrap-progress")) wrapProgress++ else unwrapProgress++
            }
        }
        MdanceContainer.wrapZipFileToMdance(source, sealed, packageName, observe)
        assertEquals(plainBytes + 36L, sealed.length())
        MdanceContainer.unwrapMdanceToZipFile(sealed, verified, packageName, observe)
        assertEquals(plainBytes, verified.length())
        assertArrayEquals(expected, digest(verified))
        assertTrue(wrapProgress >= 2 && unwrapProgress >= 2)
        assertFalse("Cipher withheld the whole payload until doFinal", nonIncremental)
        val heapGrowth = (peakHeap - beforeHeap).coerceAtLeast(0L)
        Log.i("MdContainerQA", "DEVICE_LARGE_PASS bytes=$plainBytes beforeHeap=$beforeHeap peakHeap=$peakHeap growth=$heapGrowth maxHeap=${Runtime.getRuntime().maxMemory()} elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
        assertTrue("Large-file Java heap grew by $heapGrowth bytes", heapGrowth < 64L * 1024 * 1024)
        assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test fun default_android_provider_buffering_is_measured_without_large_input_or_forcing_provider() {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ByteArray(32) { 7 }, "AES"), GCMParameterSpec(128, ByteArray(12) { 9 }))
        val block = ByteArray(64 * 1024) { 3 }
        val output = ByteArray(4 * 1024 * 1024 + 16)
        var updates = 0
        repeat(64) { updates += cipher.update(block, 0, block.size, output, 0) }
        val final = cipher.doFinal(output, 0)
        Log.i("MdContainerQA", "DEFAULT_GCM provider=${cipher.provider.name} inputBytes=${4 * 1024 * 1024} updateBytes=$updates finalBytes=$final")
        assertEquals(4 * 1024 * 1024 + 16, updates + final)
    }

    @Test fun authentication_failure_on_native_device_preserves_previous_output_and_removes_plaintext_temp() {
        val source = File(directory, "source.zip").apply { writeBytes(ByteArray(8193) { it.toByte() }) }
        val sealed = File(directory, "corrupt.dance")
        MdanceContainer.wrapZipFileToMdance(source, sealed, packageName)
        RandomAccessFile(sealed, "rw").use { file ->
            file.seek(file.length() - 1)
            val old = file.read()
            file.seek(file.length() - 1)
            file.write(old xor 1)
        }
        val sentinel = byteArrayOf(44, 45, 46)
        val output = File(directory, "verified.zip").apply { writeBytes(sentinel) }
        val failure = runCatching { MdanceContainer.unwrapMdanceToZipFile(sealed, output, packageName) }.exceptionOrNull()
        assertEquals("InvalidCipherTextException", failure?.javaClass?.simpleName)
        assertArrayEquals(sentinel, output.readBytes())
        assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    private fun usedHeap(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    private fun digest(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val block = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(block)
                if (count < 0) break
                digest.update(block, 0, count)
            }
        }
        return digest.digest()
    }
}
