package com.midnightdancer.core.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** iOS file-backed catalog DB under Documents. */
@OptIn(ExperimentalForeignApi::class)
fun catalogDatabaseBuilder(): RoomDatabase.Builder<CatalogDatabase> {
    val dbFilePath = documentDirectory() + "/$CATALOG_DATABASE_FILE_NAME"
    return Room.databaseBuilder<CatalogDatabase>(
        name = dbFilePath,
        factory = { CatalogDatabaseConstructor.initialize() },
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val url =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
    return requireNotNull(url?.path)
}
