package com.midnightdancer.core.tts

/**
 * Full-only product voices (ADR D-2026-09-22): Piper Ruslan / Irina / Kamila.
 * [id] matches `assets/voices/full/{id}/` folder names.
 */
enum class TrainerVoice(
    val id: String,
    val displayNameRu: String,
    val displayNameEn: String,
    /** BCP-47 hint for system-TTS fallback when Piper ONNX is absent. */
    val systemLocaleTag: String,
) {
    Ruslan(
        id = "ruslan",
        displayNameRu = "Руслан",
        displayNameEn = "Ruslan",
        systemLocaleTag = "ru-RU",
    ),
    Irina(
        id = "irina",
        displayNameRu = "Ирина",
        displayNameEn = "Irina",
        systemLocaleTag = "ru-RU",
    ),
    Kamila(
        id = "kamila",
        displayNameRu = "Камила",
        displayNameEn = "Kamila",
        systemLocaleTag = "en-US",
    );

    companion object {
        val fullSet: List<TrainerVoice> = entries.toList()

        fun fromId(id: String): TrainerVoice? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}
