package com.midnightdancer.core.data.db.di

import androidx.room.RoomDatabase
import com.midnightdancer.core.data.db.CatalogDatabase
import com.midnightdancer.core.data.db.RoomCatalogRepository
import com.midnightdancer.core.data.db.getCatalogDatabase
import com.midnightdancer.core.data.db.inMemoryCatalogDatabaseBuilder
import com.midnightdancer.core.data.migrate.catalog.CatalogExporter
import com.midnightdancer.core.data.migrate.catalog.CatalogMigrator
import com.midnightdancer.core.domain.usecase.LoadLibrary
import com.midnightdancer.core.domain.usecase.SaveAppData
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin DB module for C-13 Room catalog.
 *
 * @param produceBuilder platform file-path / Context builder (Android/iOS).
 */
fun catalogDatabaseModule(
    produceBuilder: () -> RoomDatabase.Builder<CatalogDatabase>,
): Module =
    module {
        single<CatalogDatabase> { getCatalogDatabase(produceBuilder()) }
        single { RoomCatalogRepository(get()) }
        single<LoadLibrary> { get<RoomCatalogRepository>() }
        single<SaveAppData> { get<RoomCatalogRepository>() }
        factory { CatalogMigrator(repository = get(), db = get()) }
        factory { CatalogExporter(repository = get()) }
    }

/** In-memory catalog for tests / Stage 2 DI smoke without platform paths. */
fun inMemoryCatalogDatabaseModule(
    name: String = "midnight_dancer_catalog_koin.db",
): Module = catalogDatabaseModule { inMemoryCatalogDatabaseBuilder(name) }
