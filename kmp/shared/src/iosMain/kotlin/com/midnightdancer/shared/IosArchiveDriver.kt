package com.midnightdancer.shared

/** Narrow Swift boundary for streaming through a private, authenticated archive. */
interface IosArchiveDriver {
    @Throws(Exception::class)
    fun decryptToPrivateZip(containerPath: String, destinationPath: String, bundleId: String, shouldCancel: () -> Boolean)

    @Throws(Exception::class)
    fun encryptPrivateZip(zipPath: String, destinationPath: String, bundleId: String, shouldCancel: () -> Boolean)
}
