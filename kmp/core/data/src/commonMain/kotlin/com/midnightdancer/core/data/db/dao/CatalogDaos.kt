package com.midnightdancer.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.midnightdancer.core.data.db.entity.CatalogMetaEntity
import com.midnightdancer.core.data.db.entity.ChoreographyEntity
import com.midnightdancer.core.data.db.entity.DanceStyleEntity
import com.midnightdancer.core.data.db.entity.MoveEntity
import com.midnightdancer.core.data.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DanceStyleDao {
    @Query("SELECT * FROM dance_styles ORDER BY name ASC")
    fun observeAll(): Flow<List<DanceStyleEntity>>

    @Query("SELECT * FROM dance_styles ORDER BY name ASC")
    suspend fun getAll(): List<DanceStyleEntity>

    @Query("SELECT * FROM dance_styles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DanceStyleEntity?

    @Upsert
    suspend fun upsert(style: DanceStyleEntity)

    @Upsert
    suspend fun upsertAll(styles: List<DanceStyleEntity>)

    @Query("DELETE FROM dance_styles")
    suspend fun deleteAll()
}

@Dao
interface MoveDao {
    @Query("SELECT * FROM moves ORDER BY style_id ASC, sort_order ASC")
    fun observeAll(): Flow<List<MoveEntity>>

    @Query("SELECT * FROM moves WHERE style_id = :styleId ORDER BY sort_order ASC")
    fun observeByStyle(styleId: String): Flow<List<MoveEntity>>

    @Query("SELECT * FROM moves ORDER BY style_id ASC, sort_order ASC")
    suspend fun getAll(): List<MoveEntity>

    @Query("SELECT * FROM moves WHERE style_id = :styleId ORDER BY sort_order ASC")
    suspend fun getByStyle(styleId: String): List<MoveEntity>

    @Upsert
    suspend fun upsert(move: MoveEntity)

    @Upsert
    suspend fun upsertAll(moves: List<MoveEntity>)

    @Query("DELETE FROM moves WHERE style_id = :styleId")
    suspend fun deleteByStyle(styleId: String)

    @Query("DELETE FROM moves")
    suspend fun deleteAll()
}

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAll(): List<SongEntity>

    @Upsert
    suspend fun upsert(song: SongEntity)

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()
}

@Dao
interface ChoreographyDao {
    @Query("SELECT * FROM choreographies ORDER BY name ASC")
    fun observeAll(): Flow<List<ChoreographyEntity>>

    @Query("SELECT * FROM choreographies ORDER BY name ASC")
    suspend fun getAll(): List<ChoreographyEntity>

    @Upsert
    suspend fun upsert(choreography: ChoreographyEntity)

    @Upsert
    suspend fun upsertAll(choreographies: List<ChoreographyEntity>)

    @Query("DELETE FROM choreographies")
    suspend fun deleteAll()
}

@Dao
interface CatalogMetaDao {
    @Query("SELECT * FROM catalog_meta WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): CatalogMetaEntity?

    @Query("SELECT value FROM catalog_meta WHERE `key` = :key LIMIT 1")
    fun observeValue(key: String): Flow<String?>

    @Upsert
    suspend fun upsert(entity: CatalogMetaEntity)

    @Query("DELETE FROM catalog_meta WHERE `key` = :key")
    suspend fun delete(key: String)
}

