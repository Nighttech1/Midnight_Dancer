package com.midnightdancer.app.choreography

import android.content.Context
import android.util.AtomicFile
import com.midnightdancer.core.domain.styles.ManageDanceStyles
import com.midnightdancer.core.domain.styles.projectVaultStyleJson
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultPersister
import java.io.File

/** C-301: JSON vault on app filesDir — survives force-stop (Client D1.3). */
class AndroidChoreographyVaultPersister(
    context: Context,
    private val danceStyles: ManageDanceStyles? = null,
) : ChoreographyVaultPersister {
    private val file = AtomicFile(File(context.filesDir, "choreography_vault_v1.json"))

    @Synchronized
    override fun readRaw(): String? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        return normalize(file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private fun normalize(raw: String): String = projectVaultStyleJson(raw, danceStyles?.state?.value)

    @Synchronized
    override fun writeRaw(json: String) {
        val bytes = normalize(json).toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output)
        } catch (error: Throwable) { file.failWrite(output); throw error }
    }
}
