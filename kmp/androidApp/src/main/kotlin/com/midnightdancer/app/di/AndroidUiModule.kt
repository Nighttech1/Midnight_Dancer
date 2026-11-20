package com.midnightdancer.app.di

import coil3.ImageLoader
import com.midnightdancer.app.choreography.AndroidChoreographyVaultPersister
import com.midnightdancer.core.data.cache.ImageCacheConfig
import com.midnightdancer.core.data.cache.buildCoilImageLoader
import com.midnightdancer.core.data.cache.di.imageCacheKoinModule
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultPersister
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
        single<com.midnightdancer.core.domain.media.OwnedMediaStore> {
            com.midnightdancer.core.data.files.AndroidOwnedMediaStore(androidContext())
        }
        single<com.midnightdancer.core.domain.settings.ChoreographyDeletedObserver> {
            val prefs = androidContext().getSharedPreferences("rehearsal_settings_v1", android.content.Context.MODE_PRIVATE)
            com.midnightdancer.core.domain.settings.ChoreographyDeletedObserver { id ->
                check(prefs.edit().remove(id).commit()) { "Не удалось удалить настройки отработки" }
            }
        }
        single<com.midnightdancer.feature.exchange.logic.SavedSetsPersister> {
            com.midnightdancer.app.exchange.AndroidSavedSetsPersister(androidContext(), get())
        }
        single<com.midnightdancer.core.domain.exchange.ChoreographyExchangeBridge> {
            com.midnightdancer.app.exchange.AndroidChoreographyExchangeBridge(androidContext(), get(), com.midnightdancer.core.data.files.OwnedMediaRuntime::resolveReference)
        }
        single<com.midnightdancer.core.domain.settings.CreationAccessPolicy> {
            com.midnightdancer.core.domain.settings.CreationAccessPolicy {
                com.midnightdancer.app.access.ProductAccessRuntime.canCreate(it)
            }
        }
        single<ImageLoader> {
            buildCoilImageLoader(
                context = androidContext(),
                config = get(),
            )
        }
        single<ChoreographyVaultPersister> {
            AndroidChoreographyVaultPersister(androidContext(), get())
        }
    },
)
