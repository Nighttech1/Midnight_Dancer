package com.midnightdancer.core.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

internal actual fun RoomDatabase.Builder<CatalogDatabase>.applyCatalogSqliteDriver(): RoomDatabase.Builder<CatalogDatabase> =
    setDriver(BundledSQLiteDriver())
