package com.midnightdancer.core.data.cache

actual fun isCoilImageLoaderFactoryAvailable(): Boolean = false

/**
 * iOS no-op stub for Stage 2.
 *
 * When Stage 3 wires images, reuse [ImageCacheConfig.Default] disk/memory limits
 * with Coil Multiplatform or a native cache that honors the same bytes/percent.
 *
 * @return always `null` until a real factory lands
 */
@Suppress("UNUSED_PARAMETER")
fun buildCoilImageLoaderStub(config: ImageCacheConfig = ImageCacheConfig.Default): Any? {
    // Documented stub — see ImageCacheFactoryNotes.iosStubReason
    return null
}
