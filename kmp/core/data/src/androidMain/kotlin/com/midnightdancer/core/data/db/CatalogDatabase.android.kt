package com.midnightdancer.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/** Android file-backed catalog DB under [Context.getDatabasePath]. */
fun catalogDatabaseBuilder(context: Context): RoomDatabase.Builder<CatalogDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(CATALOG_DATABASE_FILE_NAME)
    return Room.databaseBuilder<CatalogDatabase>(
        context = appContext,
        name = dbFile.absolutePath,
    )
}
