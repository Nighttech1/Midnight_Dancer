package com.midnightdancer.core.data.migrate

import com.midnightdancer.core.data.db.RoomCatalogRepository
import com.midnightdancer.core.data.files.IosDurableTextFile
import com.midnightdancer.core.data.files.platformDocumentsDirectoryPath
import com.midnightdancer.core.domain.serialization.DomainJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okio.FileSystem
import okio.Path.Companion.toPath

/** Keep original Flutter media in the app's Documents directory and store relocatable references. */
suspend fun migrateIosLegacyCatalog(repository: RoomCatalogRepository) = withContext(Dispatchers.IO) {
    if (repository.isIosLegacyCatalogMigrated()) return@withContext
    val documents = FileSystem.SYSTEM.canonicalize(platformDocumentsDirectoryPath().toPath())
    val legacy = documents / "MidnightDancer"
    val raw = IosDurableTextFile("MidnightDancer/metadata.json").readOrNull()
    if (raw == null) {
        check(listOf(documents, legacy).none { directory ->
            FileSystem.SYSTEM.exists(directory / "midnight_dancer.hive") ||
                FileSystem.SYSTEM.exists(directory / "midnight_dancer.hivec")
        }) { "Найдена библиотека Hive. Автоматический перенос этого формата недоступен." }
        return@withContext
    }
    val root = DomainJson.format.parseToJsonElement(raw) as? JsonObject
        ?: error("Старая библиотека имеет неизвестный формат")
    check(root.keys.any { it in setOf("danceStyles", "songs", "choreographies", "settings") }) {
        "Старая библиотека имеет неизвестный формат"
    }
    val data = DomainJson.decodeAppData(raw)
    val moveIds = data.danceStyles.flatMap { style -> style.moves.map { it.id } }
    check(listOf(data.danceStyles.map { it.id }, moveIds, data.songs.map { it.id }, data.choreographies.map { it.id })
        .all { ids -> ids.all { it.isNotBlank() } && ids.distinct().size == ids.size }) {
        "В старой библиотеке есть конфликт идентификаторов. Исходные файлы сохранены."
    }
    val styles = data.danceStyles.map { style ->
        style.copy(moves = style.moves.map { move ->
            move.copy(videoUri = move.videoUri?.let(::relocateLegacyVideo))
        })
    }
    val songs = data.songs.map { song ->
        val extension = song.fileName.substringAfterLast('.', "mp3").lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "mp3"
        val name = "${song.id}.$extension"
        if (safeFileName(name)) song.copy(fileName = "md-legacy-music/$name") else song
    }
    repository.migrateIosLegacyCatalogOnce(data.copy(danceStyles = styles, songs = songs))
}

private fun relocateLegacyVideo(reference: String): String {
    val knownFolder = "/Documents/MidnightDancer/videos/"
    val storedFile = when {
        reference.startsWith("/") || reference.startsWith("file://") ->
            reference.substringAfter(knownFolder, "").takeIf(::safeFileName)
        reference.endsWith(".mp4", ignoreCase = true) && safeFileName(reference) -> reference
        safeFileName(reference) && ':' !in reference -> "$reference.mp4"
        else -> null
    }
    return storedFile?.let { "md-legacy-video/$it" } ?: reference
}

private fun safeFileName(value: String): Boolean = value.isNotBlank() && value != "." && value != ".." &&
    '/' !in value && '\\' !in value && '\u0000' !in value
