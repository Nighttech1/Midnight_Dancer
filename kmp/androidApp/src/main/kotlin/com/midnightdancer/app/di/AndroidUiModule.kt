package com.midnightdancer.app.di

import coil3.ImageLoader
import com.midnightdancer.core.data.cache.ImageCacheConfig
import com.midnightdancer.core.data.cache.buildCoilImageLoader
import com.midnightdancer.core.data.cache.di.imageCacheKoinModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android UI / Coil bindings for C-22.
 * Appended after [com.midnightdancer.shared.initKoinAndroid].
 * Exchange zip: [com.midnightdancer.core.data.exchange.di.androidExchangeZipKoinModule] in Application.
 */
fun androidUiModules(
    imageCacheConfig: ImageCacheConfig = ImageCacheConfig.Default,
): List<Module> = listOf(
    imageCacheKoinModule(imageCacheConfig),
    module {
        single<ImageLoader> {
            buildCoilImageLoader(
                context = androidContext(),
                config = get(),
            )
        }
    },
)
