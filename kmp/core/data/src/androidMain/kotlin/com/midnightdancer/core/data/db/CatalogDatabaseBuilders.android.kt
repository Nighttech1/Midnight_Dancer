package com.midnightdancer.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Holds [Context] for Room on Android (Android [Room.databaseBuilder] requires Context).
 * Call from Application / Robolectric tests before opening the catalog DB.
 */
object CatalogDbAndroidContext {
    @Volatile
    var applicationContext: Context? = null
}

fun bindCatalogDatabaseContext(context: Context) {
    CatalogDbAndroidContext.applicationContext = context.applicationContext
}

actual fun inMemoryCatalogDatabaseBuilder(
    name: String,
): RoomDatabase.Builder<CatalogDatabase> {
    val context =
        CatalogDbAndroidContext.applicationContext
            ?: error("bindCatalogDatabaseContext() must be called before Room catalog DB on Android")
    // Classic Android in-memory builder + factory for @ConstructedBy KMP database.
    return Room.inMemoryDatabaseBuilder(
        context = context,
        klass = CatalogDatabase::class.java,
    ).apply {
        // Ensure KSP-generated impl is used (ConstructedBy).
        // Room finds CatalogDatabase_Impl via reflection from klass.
    }
}
