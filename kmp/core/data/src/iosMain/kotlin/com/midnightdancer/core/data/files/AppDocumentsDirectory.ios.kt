package com.midnightdancer.core.data.files

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual fun platformDocumentsDirectoryPath(): String {
    val url =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    return requireNotNull(url?.path) { "NSDocumentDirectory unavailable" }
}

@OptIn(ExperimentalForeignApi::class)
actual fun platformFileSystemOps(): FileSystemOps = IosFileSystemOps

@OptIn(ExperimentalForeignApi::class)
private object IosFileSystemOps : FileSystemOps {
    override fun ensureDirectory(absolutePath: String) {
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = absolutePath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }
}
