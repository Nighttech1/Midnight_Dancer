package com.midnightdancer.shared

import com.arkivanov.decompose.ComponentContext
import com.midnightdancer.core.data.DataRepositoryStub
import com.midnightdancer.core.data.InMemoryDataRepositoryStub
import com.midnightdancer.core.data.db.di.inMemoryCatalogDatabaseModule
import com.midnightdancer.core.data.files.di.mediaFilesKoinModule
import com.midnightdancer.core.data.network.networkModule
import com.midnightdancer.core.data.zip.SecureZipMetadataReader
import com.midnightdancer.core.data.zip.di.secureZipKoinModule
import com.midnightdancer.core.domain.usecase.ExchangeZipUseCase
import com.midnightdancer.core.domain.usecase.ReadWriteUserPrefs
import com.midnightdancer.core.navigation.DefaultRootComponent
import com.midnightdancer.core.navigation.NavigationChildFactories
import com.midnightdancer.core.navigation.RootComponent
import com.midnightdancer.feature.choreography.logic.ChoreographyComponent
import com.midnightdancer.feature.choreography.logic.DefaultChoreographyComponent
import com.midnightdancer.feature.elements.logic.DefaultElementsComponent
import com.midnightdancer.feature.elements.logic.ElementsComponent
import com.midnightdancer.feature.exchange.logic.DefaultExchangeComponent
import com.midnightdancer.feature.exchange.logic.ExchangeComponent
import com.midnightdancer.feature.music.logic.DefaultMusicComponent
import com.midnightdancer.feature.music.logic.MusicComponent
import com.midnightdancer.feature.settings.logic.DefaultSettingsComponent
import com.midnightdancer.feature.settings.logic.SettingsComponent
import com.midnightdancer.core.tts.TtsEngine
import com.midnightdancer.feature.splash.logic.DefaultSplashComponent
import com.midnightdancer.feature.splash.logic.SplashComponent
import com.midnightdancer.feature.trainer.logic.DefaultTrainerComponent
import com.midnightdancer.feature.trainer.logic.TrainerComponent
import org.koin.core.module.Module
import org.koin.dsl.module

fun sharedCommonModule(): Module = module {
    single<DataRepositoryStub> { InMemoryDataRepositoryStub() }

    single {
        NavigationChildFactories(
            splash = { ctx, finish, auto ->
                DefaultSplashComponent(
                    componentContext = ctx,
                    finishSplash = finish,
                    autoFinish = auto,
                    ttsEngine = getOrNull<TtsEngine>(),
                )
            },
            elements = { ctx ->
                DefaultElementsComponent(
                    componentContext = ctx,
                    userPrefs = getOrNull<ReadWriteUserPrefs>(),
                )
            },
            music = { ctx -> DefaultMusicComponent(ctx) },
            choreography = { ctx -> DefaultChoreographyComponent(ctx) },
            trainer = { ctx -> DefaultTrainerComponent(ctx) },
            exchange = { ctx ->
                DefaultExchangeComponent(
                    componentContext = ctx,
                    exchangeZip = getOrNull<ExchangeZipUseCase>(),
                    deviceStorage = getOrNull(),
                )
            },
            settings = { ctx ->
                DefaultSettingsComponent(
                    componentContext = ctx,
                    userPrefs = getOrNull(),
                )
            },
        )
    }

    factory<SplashComponent> { (componentContext: ComponentContext, finishSplash: () -> Unit) ->
        DefaultSplashComponent(
            componentContext,
            finishSplash,
            autoFinish = false,
            ttsEngine = getOrNull<TtsEngine>(),
        )
    }
    factory<ElementsComponent> { (componentContext: ComponentContext) ->
        DefaultElementsComponent(
            componentContext = componentContext,
            userPrefs = getOrNull<ReadWriteUserPrefs>(),
        )
    }
    factory<MusicComponent> { (componentContext: ComponentContext) ->
        DefaultMusicComponent(componentContext)
    }
    factory<ChoreographyComponent> { (componentContext: ComponentContext) ->
        DefaultChoreographyComponent(componentContext)
    }
    factory<TrainerComponent> { (componentContext: ComponentContext) ->
        DefaultTrainerComponent(componentContext)
    }
    factory<ExchangeComponent> { (componentContext: ComponentContext) ->
        DefaultExchangeComponent(
            componentContext = componentContext,
            exchangeZip = getOrNull<ExchangeZipUseCase>(),
            deviceStorage = getOrNull(),
        )
    }
    factory<SettingsComponent> { (componentContext: ComponentContext) ->
        DefaultSettingsComponent(
            componentContext = componentContext,
            userPrefs = getOrNull<ReadWriteUserPrefs>(),
        )
    }

    factory<RootComponent> { (componentContext: ComponentContext) ->
        DefaultRootComponent(
            componentContext = componentContext,
            factories = get(),
        )
    }
}

/**
 * Default shared modules (Stage 2 + C-21 feature factories).
 *
 * C-13 Room is **not** auto-started here: Android needs
 * [com.midnightdancer.core.data.db.bindCatalogDatabaseContext] before
 * [inMemoryCatalogDatabaseModule] / [catalogDatabaseModule].
 * Platforms append [sharedCatalogModules] after binding Context / iOS path.
 *
 * C-14 MediaStorage: Android needs
 * [com.midnightdancer.core.data.files.bindMediaFilesContext] before first resolve
 * (see [initKoinAndroid]).
 *
 * C-15 secure zip: append [sharedSecureZipModules] when platform supplies
 * [SecureZipMetadataReader] (extract surface; C-16 audits I/O).
 *
 * C-18 Coil image cache: append
 * [com.midnightdancer.core.data.cache.di.imageCacheKoinModule] when needed
 * (config only; not in default list). Android ImageLoader via
 * [com.midnightdancer.core.data.cache.buildCoilImageLoader].
 *
 * C-11 DataStore prefs: append prefs Koin module when platform supplies path;
 * Settings shell uses [ReadWriteUserPrefs] via `getOrNull()` when present.
 */
fun sharedModules(): List<Module> = listOf(
    sharedCommonModule(),
    networkModule(), // C-12 Ktor HttpClient / ApiClient
    mediaFilesKoinModule(), // C-14 MidnightDancer/ music|videos|metadata.json
)

/** C-13 Room catalog modules (in-memory smoke or file builder). */
fun sharedCatalogModules(
    useInMemory: Boolean = true,
): List<Module> =
    if (useInMemory) {
        listOf(inMemoryCatalogDatabaseModule())
    } else {
        emptyList()
    }

/** C-15 validate / merge / import — requires platform metadata reader. */
fun sharedSecureZipModules(metadataReader: SecureZipMetadataReader): List<Module> =
    listOf(secureZipKoinModule(metadataReader = metadataReader))

/*
 * File-backed catalog / secure-zip factories: import
 * `catalogDatabaseModule` / `secureZipKoinModule` from `:core:data` directly.
 * Avoid `::catalogDatabaseModule` re-exports here — K2 metadata ICE on
 * RoomDatabase.Builder function refs (Android compile).
 */
