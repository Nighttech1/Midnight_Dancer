package com.midnightdancer.core.data.db

import androidx.room.RoomDatabase

/** Use Android framework SQLite — BundledSQLite native lib fails under Robolectric (UnsatisfiedLinkError). */
internal actual fun RoomDatabase.Builder<CatalogDatabase>.applyCatalogSqliteDriver(): RoomDatabase.Builder<CatalogDatabase> =
    this
