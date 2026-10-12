package com.midnightdancer.core.data.db

import androidx.room.RoomDatabase

/**
 * In-memory / process-local catalog DB builder for tests and Stage 2 DI smoke.
 * Android requires [bindCatalogDatabaseContext] first (androidMain).
 */
expect fun inMemoryCatalogDatabaseBuilder(
    name: String = "memory",
): RoomDatabase.Builder<CatalogDatabase>
