package com.midnightdancer.core.data.cache

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import okio.Path.Companion.toOkioPath
import java.io.File

actual fun isCoilImageLoaderFactoryAvailable(): Boolean = true

/**
 * Builds a Coil 3 [ImageLoader] with disk LRU + memory LRU from [config].
 *
 * Does **not** set the process singleton — callers (Application / Koin / Compose)
 * own the instance. Network fetcher comes from `coil-network-okhttp` on classpath.
 *
 * Cache files live under `[context.cacheDir]/[config.diskCacheDirectoryName]`,
 * separate from C-14 `MidnightDancer/` media documents.
 */
fun buildCoilImageLoader(
    context: Context,
    config: ImageCacheConfig = ImageCacheConfig.Default,
): ImageLoader {
    val appContext = context.applicationContext
    val diskDir = File(appContext.cacheDir, config.diskCacheDirectoryName)
    return ImageLoader.Builder(appContext)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(appContext, config.memoryMaxSizePercent)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(diskDir.toOkioPath())
                .maxSizeBytes(config.diskMaxSizeBytes)
                .build()
        }
        .build()
}
