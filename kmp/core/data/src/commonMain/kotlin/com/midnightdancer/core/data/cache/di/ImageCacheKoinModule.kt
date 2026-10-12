package com.midnightdancer.core.data.cache.di

import com.midnightdancer.core.data.cache.ImageCacheConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Opt-in Koin binding for C-18 [ImageCacheConfig].
 *
 * **Not** included in [com.midnightdancer.shared.sharedModules] by default —
 * platforms that need the constants append this module (similar to secure zip).
 * Android ImageLoader still needs Context; bind it in the app layer via
 * [com.midnightdancer.core.data.cache.buildCoilImageLoader].
 */
fun imageCacheKoinModule(
    config: ImageCacheConfig = ImageCacheConfig.Default,
): Module =
    module {
        single { config }
    }
