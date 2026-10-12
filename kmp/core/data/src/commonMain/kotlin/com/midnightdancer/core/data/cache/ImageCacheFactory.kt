package com.midnightdancer.core.data.cache

/**
 * Platform notes for C-18 Coil wiring (no UI screens in Stage 2).
 *
 * - **Android:** [buildCoilImageLoader] in `androidMain` builds a Coil 3
 *   `ImageLoader` with DiskCache + MemoryCache from [ImageCacheConfig].
 * - **iOS:** [ImageCacheFactoryNotes.iosStubReason] — no Coil ImageLoader yet;
 *   Stage 3 may use Coil KMP or a SwiftUI bridge. Config constants still apply.
 */
object ImageCacheFactoryNotes {
    const val androidReady: Boolean = true

    const val iosStubReason: String =
        "C-18: iOS ImageLoader not wired in Stage 2 (no UI). " +
            "Reuse ImageCacheConfig limits when Stage 3 adds Coil KMP / bridge."
}

/**
 * Whether this platform can build a Coil ImageLoader today.
 * Android: true. iOS: false (stub).
 */
expect fun isCoilImageLoaderFactoryAvailable(): Boolean
