package com.midnightdancer.core.data.zip

import com.midnightdancer.core.data.backup.DefaultMergeImport
import com.midnightdancer.core.data.backup.FullBackupStyleRedirect
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.exchange.StyleImportPlan
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.usecase.ImportContentFilter
import com.midnightdancer.core.domain.usecase.ImportSecureZip
import com.midnightdancer.core.domain.usecase.ImportSecureZipResult
import com.midnightdancer.core.domain.usecase.MergeImport
import com.midnightdancer.core.domain.usecase.SecureZipValidationResult
import com.midnightdancer.core.domain.usecase.ValidateSecureZip

/**
 * Reads UTF-8 `metadata.json` for a validated archive.
 *
 * **Production:** implementations MUST only return bytes from a sandbox-extracted
 * `metadata.json` that already passed zip-slip / whitelist / [SecureZipSandbox] checks
 * (Flutter STEP 3–4). Do **not** open the archive directly for metadata in release.
 *
 * **Tests:** [MapSecureZipMetadataReader] may inject JSON by path without I/O.
 *
 * Callers still must run [validateMetadataJsonBeforeDecode] (size + structure) before
 * [DomainJson.decodeAppData] — see [DefaultImportSecureZip].
 */
fun interface SecureZipMetadataReader {
    /** Returns UTF-8 metadata.json contents from a validated archive, or null. */
    fun readMetadataJson(absoluteArchivePath: String): String?
}

class DefaultImportSecureZip(
    private val validate: ValidateSecureZip,
    private val mergeImport: MergeImport = DefaultMergeImport(),
    private val metadataReader: SecureZipMetadataReader,
    private val useFullBackupMerge: Boolean = true,
) : ImportSecureZip {
    override suspend fun import(
        archivePath: String,
        into: AppData,
        contentFilter: ImportContentFilter,
        stylePlan: StyleImportPlan,
    ): ImportSecureZipResult {
        when (val v = validate.validate(archivePath)) {
            is SecureZipValidationResult.Rejected ->
                return ImportSecureZipResult.Failed(v.reasons)
            SecureZipValidationResult.Ok -> Unit
        }
        val raw =
            metadataReader.readMetadataJson(archivePath)
                ?: return ImportSecureZipResult.Failed(listOf("missing_metadata_json"))
        val structureReasons = validateMetadataJsonBeforeDecode(raw)
        if (structureReasons.isNotEmpty()) {
            return ImportSecureZipResult.Failed(structureReasons)
        }
        val decoded =
            runCatching { DomainJson.decodeAppData(raw) }.getOrElse { e ->
                return ImportSecureZipResult.Failed(
                    listOf("metadata_parse: ${e.message ?: e::class.simpleName}"),
                )
            }
        val filtered = applyContentFilter(decoded, contentFilter)
        val imported = FullBackupStyleRedirect.apply(filtered, into, stylePlan)
        val preferChoreoScoped =
            !useFullBackupMerge ||
                contentFilter.choreographyOnly ||
                (!contentFilter.importElements && contentFilter.importChoreography)
        val merge =
            if (preferChoreoScoped) {
                mergeImport.mergeChoreographyPackage(into, imported)
            } else {
                mergeImport.mergeFullBackup(into, imported)
            }
        return ImportSecureZipResult.Success(data = merge.merged, merge = merge)
    }

    private fun applyContentFilter(
        data: AppData,
        filter: ImportContentFilter,
    ): AppData {
        val ghost = preferGhost(filter)
        return data.copy(
            danceStyles =
                when {
                    filter.importElements -> data.danceStyles
                    ghost -> data.danceStyles // payload moves → ghost style in choreo merge
                    else -> emptyList()
                },
            songs =
                when {
                    filter.importMusic && !ghost -> data.songs
                    ghost -> data.songs // scoped ids in choreo merge
                    else -> emptyList()
                },
            choreographies =
                if (filter.importChoreography) data.choreographies else emptyList(),
        )
    }

    private fun preferGhost(filter: ImportContentFilter): Boolean =
        filter.choreographyOnly || (!filter.importElements && filter.importChoreography)
}

/** Test helper: metadata supplied from a map keyed by archive path. */
class MapSecureZipMetadataReader(
    private val jsonByPath: Map<String, String>,
) : SecureZipMetadataReader {
    override fun readMetadataJson(absoluteArchivePath: String): String? = jsonByPath[absoluteArchivePath]
}