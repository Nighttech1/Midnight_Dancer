package com.midnightdancer.core.data.db

import androidx.room.Room
import androidx.room.RoomDatabase

actual fun inMemoryCatalogDatabaseBuilder(
    name: String,
): RoomDatabase.Builder<CatalogDatabase> {
    val dbName =
        if (name.isBlank() || name == "memory") {
            ":memory:"
        } else {
            "file:$name?mode=memory&cache=shared"
        }
    return Room.databaseBuilder<CatalogDatabase>(
        name = dbName,
        factory = { CatalogDatabaseConstructor.initialize() },
    )
}
