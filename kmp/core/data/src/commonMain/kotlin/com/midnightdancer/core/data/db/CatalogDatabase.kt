package com.midnightdancer.core.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.midnightdancer.core.data.db.dao.CatalogMetaDao
import com.midnightdancer.core.data.db.dao.ChoreographyDao
import com.midnightdancer.core.data.db.dao.DanceStyleDao
import com.midnightdancer.core.data.db.dao.MoveDao
import com.midnightdancer.core.data.db.dao.SongDao
import com.midnightdancer.core.data.db.entity.CatalogMetaEntity
import com.midnightdancer.core.data.db.entity.ChoreographyEntity
import com.midnightdancer.core.data.db.entity.DanceStyleEntity
import com.midnightdancer.core.data.db.entity.MoveEntity
import com.midnightdancer.core.data.db.entity.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [
        DanceStyleEntity::class,
        MoveEntity::class,
        SongEntity::class,
        ChoreographyEntity::class,
        CatalogMetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(CatalogDatabaseConstructor::class)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun danceStyleDao(): DanceStyleDao

    abstract fun moveDao(): MoveDao

    abstract fun songDao(): SongDao

    abstract fun choreographyDao(): ChoreographyDao

    abstract fun catalogMetaDao(): CatalogMetaDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object CatalogDatabaseConstructor : RoomDatabaseConstructor<CatalogDatabase> {
    override fun initialize(): CatalogDatabase
}

const val CATALOG_DATABASE_FILE_NAME: String = "midnight_dancer_catalog.db"

/**
 * Shared Room configuration. Platform picks SQLite driver
 * (Android framework / Robolectric vs BundledSQLite on iOS).
 */
fun getCatalogDatabase(builder: RoomDatabase.Builder<CatalogDatabase>): CatalogDatabase =
    builder
        .applyCatalogSqliteDriver()
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/** Android: framework SQLite (works under Robolectric). iOS: BundledSQLiteDriver. */
internal expect fun RoomDatabase.Builder<CatalogDatabase>.applyCatalogSqliteDriver(): RoomDatabase.Builder<CatalogDatabase>
