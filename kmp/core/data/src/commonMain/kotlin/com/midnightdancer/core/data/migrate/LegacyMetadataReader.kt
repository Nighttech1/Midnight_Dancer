package com.midnightdancer.core.data.migrate

/**
 * Read-only access to Flutter legacy storage for one-shot prefs migration.
 *
 * Primary: `Documents/MidnightDancer/metadata.json` (filesystem).
 * Fallback: Hive box `midnight_dancer`, key `metadata` (web / failed FS).
 *
 * Platform implementations land with C-14 file paths; Stage 2 uses fixtures / stubs in tests.
 */
interface LegacyMetadataReader {
    /** UTF-8 contents of `MidnightDancer/metadata.json`, or null if missing / unreadable. */
    suspend fun readFilesystemMetadataJson(): String?

    /**
     * UTF-8 JSON string from Hive box [HIVE_BOX_NAME] key [HIVE_METADATA_KEY],
     * or null if Hive unused / empty.
     */
    suspend fun readHiveMetadataJson(): String?

    companion object {
        const val FOLDER_NAME: String = "MidnightDancer"
        const val METADATA_FILE_NAME: String = "metadata.json"
        const val HIVE_BOX_NAME: String = "midnight_dancer"
        const val HIVE_METADATA_KEY: String = "metadata"
        /** Full product only — same applicationId as Flutter full. */
        const val FULL_APPLICATION_ID: String = "com.midnightdancer.app"
    }
}

/** No legacy Flutter storage — default until C-14 wires real FS/Hive readers. */
object EmptyLegacyMetadataReader : LegacyMetadataReader {
    override suspend fun readFilesystemMetadataJson(): String? = null

    override suspend fun readHiveMetadataJson(): String? = null
}

/** In-memory / test double for [LegacyMetadataReader]. */
class FixtureLegacyMetadataReader(
    private val filesystemJson: String? = null,
    private val hiveJson: String? = null,
) : LegacyMetadataReader {
    override suspend fun readFilesystemMetadataJson(): String? = filesystemJson

    override suspend fun readHiveMetadataJson(): String? = hiveJson
}
