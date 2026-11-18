package com.midnightdancer.app.ui.trainer

import android.content.Context
import com.midnightdancer.feature.trainer.logic.RehearsalSettings
import com.midnightdancer.feature.trainer.logic.RehearsalSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AndroidRehearsalSettingsRepository(context: Context) : RehearsalSettingsRepository {
    private val prefs = context.getSharedPreferences("rehearsal_settings_v1", Context.MODE_PRIVATE)
    override suspend fun read(choreographyId: String): RehearsalSettings? = withContext(Dispatchers.IO) {
        val raw = prefs.getString(choreographyId, null) ?: return@withContext null
        val value = JSONObject(raw)
        RehearsalSettings(choreographyId, value.getString("revision"), value.getLong("start"),
            value.getLong("end"), value.getInt("passes"), value.getLong("rest"),
            value.getLong("preparation"), value.getBoolean("voice"), value.getInt("schema"))
    }
    override suspend fun write(settings: RehearsalSettings) = withContext(Dispatchers.IO) {
        val value = JSONObject().put("revision", settings.choreographyRevision).put("start", settings.startMs)
            .put("end", settings.endMs).put("passes", settings.totalPasses).put("rest", settings.restMs)
            .put("preparation", settings.preparationMs).put("voice", settings.voiceEnabled).put("schema", settings.schemaVersion)
        check(prefs.edit().putString(settings.choreographyId, value.toString()).commit()) { "Не удалось сохранить настройки отработки" }
    }
    override suspend fun delete(choreographyId: String) = withContext(Dispatchers.IO) {
        check(prefs.edit().remove(choreographyId).commit()) { "Не удалось сбросить настройки отработки" }
    }
}
