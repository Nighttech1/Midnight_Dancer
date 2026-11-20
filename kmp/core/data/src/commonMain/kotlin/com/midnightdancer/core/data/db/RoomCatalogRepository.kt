package com.midnightdancer.core.data.db

import com.midnightdancer.core.data.db.entity.CatalogMetaEntity
import com.midnightdancer.core.data.db.entity.CatalogMetaKeys
import com.midnightdancer.core.data.db.entity.DanceStyleEntity
import com.midnightdancer.core.data.db.mapper.CatalogMappers.toDomain
import com.midnightdancer.core.data.db.mapper.CatalogMappers.toEntity
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.Choreography
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.entity.Song
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.usecase.LoadLibrary
import com.midnightdancer.core.domain.usecase.SaveAppData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.JsonObject
import com.midnightdancer.core.domain.styles.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Room-backed catalog repository implementing C-10 [LoadLibrary] / [SaveAppData].
 * Style 1—N Move; timeline persisted as JSON; settings blob in catalog_meta (export parity).
 */
class RoomCatalogRepository(
    private val db: CatalogDatabase,
) : LoadLibrary, SaveAppData, ManageDanceStyles {
    private val styles = db.danceStyleDao()
    private val moves = db.moveDao()
    private val songs = db.songDao()
    private val choreos = db.choreographyDao()
    private val meta = db.catalogMetaDao()
    private val styleState = MutableStateFlow<DanceStyleCatalogSnapshot?>(null)
    override val state: StateFlow<DanceStyleCatalogSnapshot?> = styleState.asStateFlow()

    private suspend fun storedStyles(): DanceStyleCatalogSnapshot? =
        meta.get(STYLE_META)?.value?.let { DomainJson.format.decodeFromString(DanceStyleCatalogSnapshot.serializer(), it) }

    private suspend fun storeStyles(snapshot: DanceStyleCatalogSnapshot) {
        meta.upsert(CatalogMetaEntity(STYLE_META, DomainJson.format.encodeToString(DanceStyleCatalogSnapshot.serializer(), snapshot)))
    }

    private fun publishStyles(snapshot: DanceStyleCatalogSnapshot) {
        styleState.update { old -> if ((old?.revision ?: -1L) <= snapshot.revision) snapshot else old }
    }

    override suspend fun bootstrap(legacyNames: List<String>) {
        val snapshot = db.withTransaction {
            storedStyles()?.let { return@withTransaction it }
            val data = assembleAppData()
            val entries = data.danceStyles.filterNot { it.id == "__md_choreo_scoped__" }.map { DanceStyleEntry(it.id, it.name) }.toMutableList()
            val labels = legacyNames + data.songs.map { it.danceStyle } + data.choreographies.map { it.styleId }
            labels.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { name ->
                if (entries.none { it.id == name || it.name.equals(name, true) } &&
                    !name.equals(ReservedDanceStyle.NAME, true) && name != ReservedDanceStyle.ID) {
                    var n = entries.size + 1
                    while (entries.any { it.id == "md-style-$n" }) n++
                    entries += DanceStyleEntry("md-style-$n", name)
                }
            }
            val reservedAliases = entries.filter { it.id != ReservedDanceStyle.ID && it.name.equals(ReservedDanceStyle.NAME, true) }
            val current = DanceStyleCatalogSnapshot(1,
                listOf(DanceStyleEntry(ReservedDanceStyle.ID, ReservedDanceStyle.NAME, true)) +
                    entries.filter { it.id != ReservedDanceStyle.ID && it !in reservedAliases },
                reservedAliases.associate { it.id to ReservedDanceStyle.ID },
            )
            val normalized = data.normalizeLocalStyles(current)
            replaceCatalog(normalized)
            storeStyles(current)
            current
        }
        publishStyles(snapshot)
    }

    override suspend fun add(name: String) {
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= 24) { "Укажите название стиля до 24 символов." }
        mutateStyles { old ->
            require(old.entries.none { it.name.equals(clean, true) }) { "Стиль с таким названием уже существует." }
            var n = old.revision + 1
            while (old.entries.any { it.id == "md-style-$n" } || "md-style-$n" in old.redirects) n++
            old.copy(entries = old.entries + DanceStyleEntry("md-style-$n", clean))
        }
    }

    override suspend fun rename(id: String, name: String) {
        val clean = name.trim()
        require(id != ReservedDanceStyle.ID) { "Стиль «Без стиля» нельзя переименовать." }
        require(clean.isNotEmpty() && clean.length <= 24) { "Укажите название стиля до 24 символов." }
        mutateStyles { old ->
            val entry = old.entries.firstOrNull { it.id == id } ?: error("Стиль недоступен.")
            require(old.entries.none { it.id != id && it.name.equals(clean, true) }) { "Стиль с таким названием уже существует." }
            old.copy(entries = old.entries.map { if (it.id == id) it.copy(name = clean) else it },
                legacyAliases = old.legacyAliases + (entry.name.lowercase() to id))
        }
    }

    override suspend fun remove(id: String) {
        require(id != ReservedDanceStyle.ID) { "Стиль «Без стиля» нельзя удалить." }
        mutateStyles { old ->
            val entry = old.entries.firstOrNull { it.id == id } ?: return@mutateStyles old
            old.copy(entries = old.entries.filterNot { it.id == id },
                redirects = old.redirects.mapValues { if (it.value == id) ReservedDanceStyle.ID else it.value } + (id to ReservedDanceStyle.ID),
                legacyAliases = old.legacyAliases.mapValues { if (it.value == id) ReservedDanceStyle.ID else it.value } +
                    (entry.name.lowercase() to ReservedDanceStyle.ID))
        }
    }

    private suspend fun mutateStyles(change: (DanceStyleCatalogSnapshot) -> DanceStyleCatalogSnapshot) {
        val next = db.withTransaction {
            val old = storedStyles() ?: error("Каталог стилей ещё загружается.")
            val next = change(old).copy(revision = old.revision + 1)
            // Read before replacing; FK CASCADE can only affect the discarded old rows.
            val normalized = assembleAppData().normalizeLocalStyles(next)
            replaceCatalog(normalized)
            storeStyles(next)
            next
        }
        publishStyles(next)
    }

    private suspend fun replaceCatalog(data: AppData) {
        moves.deleteAll()
        styles.deleteAll()
        songs.deleteAll()
        choreos.deleteAll()
        writeCatalog(data)
    }

    override suspend fun load(): AppData = db.withTransaction { assembleAppData() }

    override fun observe(): Flow<AppData> =
        combine(
            styles.observeAll(),
            moves.observeAll(),
            songs.observeAll(),
            choreos.observeAll(),
            meta.observeValue(CatalogMetaKeys.SETTINGS_JSON),
        ) { _, _, _, _, _ ->
            // DAO invalidations are independent. Never expose a half-old/half-new style reassignment.
            load()
        }

    override suspend fun save(data: AppData) {
        var committed: DanceStyleCatalogSnapshot? = null
        db.withTransaction {
            val snapshot = storedStyles()
            if (snapshot == null) replaceCatalog(data)
            else {
                val incoming = data.danceStyles.filter { row -> row.id != "__md_choreo_scoped__" && snapshot.resolveId(row.id) == null }
                val reservedAliases = incoming.filter { it.name.equals(ReservedDanceStyle.NAME, true) }
                val next = snapshot.copy(revision = snapshot.revision + 1,
                    entries = snapshot.entries + incoming.filterNot { it in reservedAliases }.map { DanceStyleEntry(it.id, it.name) },
                    redirects = snapshot.redirects + reservedAliases.associate { it.id to ReservedDanceStyle.ID })
                replaceCatalog(data.normalizeLocalStyles(next))
                storeStyles(next)
                committed = next
            }
        }
        committed?.let(::publishStyles)
    }

    override suspend fun upsertSong(song: Song) {
        db.withTransaction { val snapshot = storedStyles()
            songs.upsert((if (snapshot == null) song else song.copy(danceStyle = snapshot.canonicalId(song.danceStyle))).toEntity()) }
    }

    override suspend fun upsertMove(styleId: String, move: Move) {
        db.withTransaction {
        val styleId = if (styleId == "__md_choreo_scoped__") styleId else storedStyles()?.canonicalId(styleId) ?: styleId
        val existing = moves.getByStyle(styleId)
        val order =
            existing.indexOfFirst { it.id == move.id }.takeIf { it >= 0 }
                ?: existing.size
        moves.upsert(move.toEntity(styleId = styleId, sortOrder = order))
        }
    }

    override suspend fun upsertStyle(style: DanceStyle) {
        db.withTransaction {
            val catalog = storedStyles()
            if (catalog != null && style.id != "__md_choreo_scoped__" && catalog.canonicalId(style.id) != style.id) {
                // A stale style snapshot must append its material identities, never replace reserved.
                style.moves.forEach { upsertMove(catalog.canonicalId(style.id), it) }
                return@withTransaction
            }
            val style = catalog?.entries?.firstOrNull { it.id == style.id }?.let { style.copy(name = it.name) } ?: style
            styles.upsert(style.toEntity())
            moves.deleteByStyle(style.id)
            moves.upsertAll(
                style.moves.mapIndexed { index, move ->
                    move.toEntity(styleId = style.id, sortOrder = index)
                },
            )
        }
    }

    override suspend fun upsertChoreography(choreography: Choreography) {
        db.withTransaction {
            val snapshot = storedStyles()
            val next = if (snapshot == null) choreography else choreography.copy(
                styleId = snapshot.canonicalId(choreography.styleId),
                timeline = choreography.timeline.mapValues { (_, ref) ->
                    val index = ref.indexOf("::")
                    if (index < 0 || ref.substring(0, index) == "__md_choreo_scoped__") ref
                    else snapshot.canonicalId(ref.substring(0, index)) + ref.substring(index)
                })
            choreos.upsert(next.toEntity())
        }
    }

    override suspend fun ensureDanceStyle(
        id: String,
        name: String,
        currentMoveId: String?,
    ) {
        db.withTransaction {
        val snapshot = storedStyles()
        val canonical = if (id == "__md_choreo_scoped__") id else snapshot?.canonicalId(id) ?: id
        if (canonical != id) return@withTransaction
        val existing = styles.getById(id)
        styles.upsert(
            DanceStyleEntity(
                id = id,
                name = snapshot?.entries?.firstOrNull { it.id == id }?.name ?: name.ifBlank { existing?.name ?: id },
                currentMoveId = currentMoveId ?: existing?.currentMoveId,
            ),
        )
        }
    }

    override suspend fun deleteMove(id: String) {
        moves.deleteById(id)
    }

    override suspend fun deleteSong(id: String) {
        songs.deleteById(id)
    }

    suspend fun isIosLegacyCatalogMigrated(): Boolean =
        meta.get(IOS_LEGACY_MIGRATION_KEY)?.value == "1"

    /** Commit user data and the migration marker together, before the app can open its editors. */
    suspend fun migrateIosLegacyCatalogOnce(data: AppData) {
        db.withTransaction {
            if (isIosLegacyCatalogMigrated()) return@withTransaction
            val existing = assembleAppData()
            check(existing == AppData()) { "A populated catalog requires a separate legacy reconciliation" }
            writeCatalog(data)
            meta.upsert(CatalogMetaEntity(key = IOS_LEGACY_MIGRATION_KEY, value = "1"))
        }
    }

    /** Idempotent upsert of full AppData (used by catalog migrator). */
    suspend fun upsertAllIdempotent(data: AppData) {
        db.withTransaction {
            writeCatalog(data)
        }
    }

    private suspend fun writeCatalog(data: AppData) {
        styles.upsertAll(data.danceStyles.map { it.toEntity() })
        val moveEntities =
            data.danceStyles.flatMap { style ->
                style.moves.mapIndexed { index, move ->
                    move.toEntity(styleId = style.id, sortOrder = index)
                }
            }
        moves.upsertAll(moveEntities)
        songs.upsertAll(data.songs.map { it.toEntity() })
        choreos.upsertAll(data.choreographies.map { it.toEntity() })
        meta.upsert(
            CatalogMetaEntity(
                key = CatalogMetaKeys.SETTINGS_JSON,
                value = DomainJson.format.encodeToString(JsonObject.serializer(), data.settings),
            ),
        )
    }

    private suspend fun assembleAppData(): AppData {
        val styleRows = styles.getAll()
        val moveRows = moves.getAll().groupBy { it.styleId }
        return AppData(
            danceStyles =
                styleRows.map { style ->
                    style.toDomain(
                        moves = (moveRows[style.id].orEmpty()).map { it.toDomain() },
                    )
                },
            songs = songs.getAll().map { it.toDomain() },
            choreographies = choreos.getAll().map { it.toDomain() },
            settings = decodeSettings(meta.get(CatalogMetaKeys.SETTINGS_JSON)?.value),
        )
    }

    private fun decodeSettings(raw: String?): JsonObject {
        if (raw.isNullOrBlank()) return JsonObject(emptyMap())
        return runCatching {
            DomainJson.format.decodeFromString(JsonObject.serializer(), raw)
        }.getOrElse { JsonObject(emptyMap()) }
    }

    private companion object { const val STYLE_META = "canonical_styles_v1" }
}


private const val IOS_LEGACY_MIGRATION_KEY = "ios_flutter_catalog_v1"
