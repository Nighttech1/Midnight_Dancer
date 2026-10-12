package com.midnightdancer.core.data.storage

import com.midnightdancer.core.domain.storage.DeviceStorage
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSFileSystemSize
import platform.Foundation.NSHomeDirectory

actual fun createDeviceStorage(): DeviceStorage = IosDeviceStorage()

private class IosDeviceStorage : DeviceStorage {
    override fun freeBytes(): Long = attr(NSFileSystemFreeSize)

    override fun totalBytes(): Long = attr(NSFileSystemSize)

    private fun attr(key: String): Long {
        val attrs =
            NSFileManager.defaultManager.attributesOfFileSystemForPath(NSHomeDirectory(), null)
                ?: return 0L
        val number = attrs[key] as? platform.Foundation.NSNumber ?: return 0L
        return number.longLongValue
    }
}
