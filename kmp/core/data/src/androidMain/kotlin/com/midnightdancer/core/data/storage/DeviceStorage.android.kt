package com.midnightdancer.core.data.storage

import android.os.Environment
import android.os.StatFs
import com.midnightdancer.core.domain.storage.DeviceStorage

actual fun createDeviceStorage(): DeviceStorage = AndroidDeviceStorage()

private class AndroidDeviceStorage : DeviceStorage {
    override fun freeBytes(): Long =
        runCatching {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            stat.availableBlocksLong * stat.blockSizeLong
        }.getOrDefault(0L)

    override fun totalBytes(): Long =
        runCatching {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            stat.blockCountLong * stat.blockSizeLong
        }.getOrDefault(0L)
}
