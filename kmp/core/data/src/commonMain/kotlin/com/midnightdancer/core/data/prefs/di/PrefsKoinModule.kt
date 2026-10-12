package com.midnightdancer.core.data.prefs.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.midnightdancer.core.data.migrate.EmptyLegacyMetadataReader
import com.midnightdancer.core.data.migrate.LegacyMetadataReader
import com.midnightdancer.core.data.migrate.PrefsMigrator
import com.midnightdancer.core.data.prefs.DataStoreReadWriteUserPrefs
import com.midnightdancer.core.data.prefs.USER_PREFS_DATA_STORE_FILE_NAME
import com.midnightdancer.core.data.prefs.createUserPrefsDataStore
import com.midnightdancer.core.domain.usecase.ReadWriteUserPrefs
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for DataStore user prefs + Flutter prefs migrator.
 *
 * [LegacyMetadataReader] defaults to [EmptyLegacyMetadataReader]; platform / C-14 overrides
 * with real filesystem + Hive readers (`single(override = true) { … }`).
 *
 * @param producePrefsPath absolute path ending with [USER_PREFS_DATA_STORE_FILE_NAME]
 */
fun prefsKoinModule(producePrefsPath: () -> String): Module =
    module {
        single<DataStore<Preferences>> {
            createUserPrefsDataStore(producePath = producePrefsPath)
        }
        single<DataStoreReadWriteUserPrefs> { DataStoreReadWriteUserPrefs(get()) }
        single<ReadWriteUserPrefs> { get<DataStoreReadWriteUserPrefs>() }
        single<LegacyMetadataReader> { EmptyLegacyMetadataReader }
        factory { PrefsMigrator(legacyReader = get(), userPrefs = get()) }
    }

/**
 * Prefs-only bindings when [DataStore] is already registered.
 * Registers empty legacy reader if none present.
 */
fun prefsBindingsModule(): Module =
    module {
        single<DataStoreReadWriteUserPrefs> { DataStoreReadWriteUserPrefs(get()) }
        single<ReadWriteUserPrefs> { get<DataStoreReadWriteUserPrefs>() }
        single<LegacyMetadataReader> { EmptyLegacyMetadataReader }
        factory { PrefsMigrator(legacyReader = get(), userPrefs = get()) }
    }
