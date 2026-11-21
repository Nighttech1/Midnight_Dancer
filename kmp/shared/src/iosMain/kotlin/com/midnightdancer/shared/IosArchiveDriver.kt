package com.midnightdancer.shared

/** Narrow Swift boundary for streaming through a private, authenticated archive. */
interface IosArchiveDriver {
    fun resolveMedia(reference: String): String?
    fun mediaSize(path: String): Long

    @Throws(Exception::class)
    fun mediaSha256(path: String): String

    @Throws(Exception::class)
    fun decryptToPrivateZip(containerPath: String, destinationPath: String, bundleId: String, shouldCancel: () -> Boolean)

    @Throws(Exception::class)
    fun encryptPrivateZip(zipPath: String, destinationPath: String, bundleId: String, shouldCancel: () -> Boolean)
}


internal class IosArchiveCodecAdapter(private val driver: IosArchiveDriver) :
    com.midnightdancer.core.data.exchange.IosArchiveContainerCodec {
    override fun decryptToPrivateZip(containerPath: String, destinationPath: String, bundleId: String, shouldCancel: () -> Boolean) =
        driver.decryptToPrivateZip(containerPath, destinationPath, bundleId, shouldCancel)

    override fun encryptPrivateZip(zipPath: String, destinationPath: String, bundleId: String, shouldCancel: () -> Boolean) =
        driver.encryptPrivateZip(zipPath, destinationPath, bundleId, shouldCancel)
}
