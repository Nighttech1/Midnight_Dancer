package com.midnightdancer.core.data.exchange

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/** Run this standalone entry point with -Xmx64m; whole-file buffering cannot pass a 192 MiB roundtrip. */
object MdanceContainerLowHeapProbe {
    @JvmStatic fun main(args: Array<String>) {
        check(Runtime.getRuntime().maxMemory() <= 96L * 1024 * 1024) { "Run probe with java -Xmx64m" }
        val parent = File(args.single()).canonicalFile.apply { check(mkdirs() || isDirectory) }
        val directory = Files.createTempDirectory(parent.toPath(), "mdance-lowheap-").toFile()
        try {
            val source = File(directory, "source.zip")
            source.outputStream().use { output ->
                val block = ByteArray(64 * 1024) { (it * 29 + 3).toByte() }
                repeat(192 * 16) { output.write(block) }
            }
            val expected = digest(source)
            val sealed = File(directory, "large.dance")
            val output = File(directory, "verified.zip")
            val started = System.nanoTime()
            MdanceContainer.wrapZipFileToMdance(source, sealed, "com.midnightdancer.app") { println(it) }
            MdanceContainer.unwrapMdanceToZipFile(sealed, output, "com.midnightdancer.app", diagnostic = { println(it) })
            check(source.length() == output.length() && expected.contentEquals(digest(output))) { "large_roundtrip_mismatch" }
            println("LOW_HEAP_PASS bytes=${source.length()} maxHeap=${Runtime.getRuntime().maxMemory()} elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
        } finally {
            check(directory.canonicalFile.parentFile == parent)
            check(directory.name.startsWith("mdance-lowheap-"))
            directory.deleteRecursively()
        }
    }

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
