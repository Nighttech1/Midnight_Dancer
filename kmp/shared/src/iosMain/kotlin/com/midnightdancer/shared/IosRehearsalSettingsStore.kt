package com.midnightdancer.shared

import com.midnightdancer.core.data.files.IosDurableTextFile
import com.midnightdancer.feature.trainer.logic.RehearsalSettings
import com.midnightdancer.feature.trainer.logic.RehearsalSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** One lock covers read/modify/atomic replacement across every retained runtime. */
internal object IosRehearsalSettingsStore : RehearsalSettingsRepository {
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun file() = IosDurableTextFile("rehearsal_settings_v1.json")
    private fun document(): JsonObject {
        val raw = file().readOrNull() ?: return buildJsonObject { put("version", 1); put("settings", JsonObject(emptyMap())) }
        require(raw.encodeToByteArray().size <= 1024 * 1024) { "Настройки отработки слишком велики. Прежние данные сохранены." }
        val document = json.parseToJsonElement(raw) as? JsonObject ?: error("Некорректные настройки отработки")
        require(document["version"]?.jsonPrimitive?.intOrNull == 1 && document["settings"] is JsonObject) {
            "Версия настроек отработки не поддерживается. Прежние данные сохранены."
        }
        return document
    }
    override suspend fun read(choreographyId: String): RehearsalSettings? = withContext(Dispatchers.Default) {
        lock.withLock {
            val encoded = document().getValue("settings").jsonObject[choreographyId] ?: return@withLock null
            json.decodeFromJsonElement(RehearsalSettings.serializer(), encoded).also {
                require(it.schemaVersion == 1 && it.choreographyId == choreographyId) { "Некорректные настройки отработки" }
            }
        }
    }
    override suspend fun write(settings: RehearsalSettings) = mutate(settings.choreographyId, json.encodeToJsonElement(RehearsalSettings.serializer(), settings))
    override suspend fun delete(choreographyId: String) = mutate(choreographyId, null)
    private suspend fun mutate(id: String, value: JsonElement?): Unit = withContext(Dispatchers.Default) {
        lock.withLock {
            val document = document()
            val settings = document.getValue("settings").jsonObject.toMutableMap()
            if (value == null) settings.remove(id) else settings[id] = value
            val next = JsonObject(document.toMutableMap().apply { put("settings", JsonObject(settings)) }).toString()
            require(next.encodeToByteArray().size <= 1024 * 1024) { "Не удалось записать настройки отработки. Прежние данные сохранены." }
            file().write(next)
        }
    }
}
