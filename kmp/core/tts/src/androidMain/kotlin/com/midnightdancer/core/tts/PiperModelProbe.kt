package com.midnightdancer.core.tts

import java.io.File

/**
 * Locates on-demand Piper assets without loading ONNX into memory.
 *
 * Search order per voice:
 * 1. `{filesDir}/voices/full/{id}/` (on-demand install)
 * 2. `{filesDir}/tts_cache/full/{id}/` (Flutter-compat cache layout)
 *
 * Never loads all three models at once — probe is file-existence only.
 */
object PiperModelProbe {
    data class ModelPaths(
        val modelDir: File,
        val modelFile: File,
        val tokensFile: File,
    )

    fun find(voice: TrainerVoice, filesDir: File): ModelPaths? {
        val candidates =
            listOf(
                File(filesDir, "${VoiceAssetPolicy.ON_DEMAND_RELATIVE_ROOT}/${voice.id}"),
                File(filesDir, "tts_cache/${VoiceAssetPolicy.FLAVOR_FOLDER}/${voice.id}"),
            )
        for (dir in candidates) {
            resolveInDir(dir, voice)?.let { return it }
        }
        return null
    }

    fun resolveInDir(dir: File, voice: TrainerVoice): ModelPaths? {
        if (!dir.isDirectory) return null
        val tokens = File(dir, VoiceAssetPolicy.TOKENS_FILE)
        if (!tokens.isFile) return null
        val preferred = File(dir, VoiceAssetPolicy.onnxFileName(voice))
        val alias = File(dir, VoiceAssetPolicy.MODEL_ALIAS)
        val model =
            when {
                preferred.isFile -> preferred
                alias.isFile -> alias
                else -> dir.listFiles()?.firstOrNull { it.name.endsWith(".onnx") }
            } ?: return null
        return ModelPaths(modelDir = dir, modelFile = model, tokensFile = tokens)
    }

    /** Approx size of one ONNX on disk (MB); 0 if missing. */
    fun approxSizeMb(paths: ModelPaths): Long =
        (paths.modelFile.length() / (1024L * 1024L)).coerceAtLeast(0)
}
