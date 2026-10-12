package com.midnightdancer.core.data.files.di

import com.midnightdancer.core.data.files.AppFiles
import com.midnightdancer.core.data.files.DefaultMediaStorage
import com.midnightdancer.core.data.files.FileSystemOps
import com.midnightdancer.core.data.files.MediaStorage
import com.midnightdancer.core.data.files.platformDocumentsDirectoryPath
import com.midnightdancer.core.data.files.platformFileSystemOps
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin bindings for C-14 media file paths.
 *
 * Android: call [com.midnightdancer.core.data.files.bindMediaFilesContext] before first resolve.
 * Tests: pass an explicit [produceDocumentsPath] (temp / fake root).
 */
fun mediaFilesKoinModule(
    produceDocumentsPath: () -> String = ::platformDocumentsDirectoryPath,
    fileSystem: FileSystemOps = platformFileSystemOps(),
): Module =
    module {
        single { AppFiles(produceDocumentsPath) }
        single<MediaStorage> {
            DefaultMediaStorage(appFiles = get(), fileSystem = fileSystem)
        }
    }
